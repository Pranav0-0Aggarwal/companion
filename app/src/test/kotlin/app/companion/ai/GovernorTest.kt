package app.companion.ai

import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class GovernorTest {
    private val big = listOf("bert", "chat", "nux").map { Spec(it, "$it.bin", 1, "0", "t", 60_000) }
    private val live = AtomicInteger()
    private val peak = AtomicInteger()

    private inner class Fake : Runner {
        override val accel = "cpu"

        init {
            peak.accumulateAndGet(live.incrementAndGet(), ::maxOf)
        }

        override fun close() {
            live.decrementAndGet()
        }
    }

    @Test
    fun `big models are never resident together`() = runBlocking<Unit> {
        val gov = Governor({ true }, { 0L })
        List(60) { i -> launch(Dispatchers.Default) { gov.run(big[i % big.size], ::Fake) { Thread.sleep(1) } } }.joinAll()
        assertEquals(1, peak.get())
        gov.release()
    }

    @Test
    fun `the next model waits while one is running`() = runBlocking<Unit> {
        val gov = Governor({ true }, { 0L })
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        var second = false
        val first = launch(Dispatchers.Default) { gov.run(big[0], ::Fake) { runBlocking { started.complete(Unit); gate.await() } } }
        started.await()
        val next = launch(Dispatchers.Default) { gov.run(big[1], ::Fake) { second = true } }
        delay(150)
        assertFalse(second)
        assertEquals(1, live.get())
        gate.complete(Unit)
        joinAll(first, next)
        assertTrue(second)
        assertEquals(1, peak.get())
        assertEquals("chat", gov.live.value?.name)
        gov.release()
    }

    @Test
    fun `a model is reused and a low memory phone refuses to load`() = runBlocking<Unit> {
        val gov = Governor({ true }, { 0L })
        repeat(3) { gov.run(big[0], ::Fake) { } }
        assertEquals(1, peak.get())
        gov.release()
        assertFailsWith<LowMemory> { Governor({ false }, { 0L }).run(big[1], ::Fake) { } }
    }
}
