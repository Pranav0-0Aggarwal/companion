package app.companion.link

import app.companion.core.CallRestore
import app.companion.link.feature.CallRelay
import app.companion.link.feature.airborne
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

class CallRelayTest {
    private val ok: Obj = mapOf("ok" to true)

    private fun go(on: () -> Obj, off: () -> Obj, run: suspend () -> Unit = {}): Pair<List<Boolean>, List<Pair<Int, Boolean>>> {
        val sent = mutableListOf<Boolean>()
        val back = mutableListOf<Pair<Int, Boolean>>()
        runCatching {
            runBlocking { airborne({ sent += it; if (it) on() else off() }, CallRestore(5, false), { v, m -> back += v to m }, run) }
        }
        return sent to back
    }

    @Test
    fun `on timeout still sends off and restores locally only when off fails`() {
        val (sent, back) = go({ throw LinkBad("bridge") }, { throw LinkBad("bridge") })
        assertEquals(listOf(true, false), sent)
        assertEquals(listOf(5 to false), back)
        val (sent2, back2) = go({ throw LinkBad("bridge") }, { ok })
        assertEquals(listOf(true, false), sent2)
        assertEquals(emptyList(), back2)
    }

    @Test
    fun `a failed run sends off once and restores once only if off fails`() {
        val (sent, back) = go({ ok }, { throw LinkBad("bridge") }, { throw LinkBad("bridge") })
        assertEquals(listOf(true, false), sent)
        assertEquals(listOf(5 to false), back)
        assertEquals(emptyList(), go({ ok }, { ok }, { throw LinkBad("bridge") }).second)
    }

    @Test
    fun `a refused on carries the bridge error and still sends off`() {
        var sent = emptyList<Boolean>()
        val e = assertFailsWith<LinkBad> {
            runBlocking { airborne({ sent += it; mapOf("ok" to false, "err" to "nointercept") }, CallRestore(1, true), { _, _ -> }) {} }
        }
        assertEquals("nointercept", e.message)
        assertEquals(listOf(true, false), sent)
    }

    @Test
    fun `a bridge timeout while up is not blamed on powers`() {
        assertEquals("powers", CallRelay.whyOf("bridge", false))
        assertEquals("audio", CallRelay.whyOf("bridge", true))
        assertEquals("nointercept", CallRelay.whyOf("nointercept", true))
        assertEquals("audio", CallRelay.whyOf("Permission denial: x", true))
        assertEquals("audio", CallRelay.whyOf(null, true))
    }
}
