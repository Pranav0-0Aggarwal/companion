package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class BatchTest {
    private val ids = (1L..100L).toList()

    private fun page(p: Progress) = ids.filter { it > p.pos && it <= p.cap }.take(Batch.size(Batch.left(p)))

    private fun run(from: Progress, batches: Int = Int.MAX_VALUE): Progress {
        var p = from
        var n = 0
        while (Batch.left(p) > 0 && n++ < batches) {
            val rows = page(p).ifEmpty { break }
            p = Batch.step(p, rows.last(), rows.size, moved = rows.size / 2)
        }
        return p
    }

    private fun fresh(sha: String? = "a") = Batch.fit(Batch.start(0, 100, sha), ids.size)

    @Test
    fun `batches hold about thirty two items and never overshoot`() {
        assertEquals(32, Batch.size(500))
        assertEquals(32, Batch.size(32))
        assertEquals(7, Batch.size(7))
        assertEquals(0, Batch.size(0))
        assertEquals(0, Batch.size(-3))
        assertEquals(10, Batch.size(500, 10))
    }

    @Test
    fun `a full run walks every item once in order`() {
        val p = run(fresh())
        assertEquals(100, p.done)
        assertEquals(100L, p.pos)
        assertEquals(0, Batch.left(p))
    }

    @Test
    fun `a run stopped midway resumes after the last checkpoint`() {
        val saved = run(fresh(), batches = 2)
        assertEquals(64, saved.done)
        assertEquals(64L, saved.pos)
        val again = Batch.resume(saved, "a")!!
        assertSame(saved, again)
        val fit = Batch.fit(again, ids.count { it > again.pos && it <= again.cap })
        val end = run(fit)
        assertEquals(100, end.done)
        assertEquals(100, end.total)
        assertEquals(50, end.moved)
    }

    @Test
    fun `a new model restarts from the beginning`() {
        val saved = run(fresh("a"), batches = 2)
        assertNull(Batch.resume(saved, "b"))
        assertNull(Batch.resume(saved, null))
        assertNull(Batch.resume(null, "a"))
    }

    @Test
    fun `new items past the cap are left out of the run`() {
        val p = Batch.fit(Batch.start(0, 40, "a"), 40)
        val end = run(p)
        assertEquals(40, end.done)
        assertEquals(40L, end.pos)
    }

    @Test
    fun `fit keeps what was done and counts what remains`() {
        val p = Progress(10, 100, 10, 0, 3, 1, 0, "a")
        assertEquals(60, Batch.fit(p, 50).total)
        assertEquals(10, Batch.fit(p, -4).total)
        assertEquals(50, Batch.left(Batch.fit(p, 50)))
    }

    @Test
    fun `counters accumulate across steps`() {
        val p = Batch.step(Batch.step(fresh(), 32, 32, 5, 2, 1), 64, 32, 4, 0, 3)
        assertEquals(64, p.done)
        assertEquals(9, p.moved)
        assertEquals(2, p.ask)
        assertEquals(4, p.skip)
    }

    @Test
    fun `an import cursor counts down`() {
        val p = Batch.fit(Batch.start(Long.MAX_VALUE, 0, null), 90)
        val q = Batch.step(p, 4_000, 32)
        assertEquals(4_000L, q.pos)
        assertEquals(58, Batch.left(q))
    }
}
