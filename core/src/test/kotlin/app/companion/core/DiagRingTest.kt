package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals

class DiagRingTest {
    private fun line(at: Long) = DiagLine(at, 50, "m$at")

    @Test
    fun `adding past the cap drops the oldest`() {
        val r = DiagRing(3)
        (1L..5L).forEach { r.add(line(it)) }
        assertEquals(listOf(3L, 4L, 5L), r.all().map { it.at })
    }

    @Test
    fun `since returns only lines after the timestamp`() {
        val r = DiagRing()
        (1L..5L).forEach { r.add(line(it)) }
        assertEquals(listOf(4L, 5L), r.since(3).map { it.at })
        assertEquals(emptyList(), r.since(5))
        assertEquals(5, r.since(0).size)
    }

    @Test
    fun `paging forward returns the oldest slice then the rest`() {
        val r = DiagRing()
        (1L..5L).forEach { r.add(line(it)) }
        val a = r.since(0, 3)
        assertEquals(listOf(1L, 2L, 3L), a.map { it.at })
        assertEquals(listOf(4L, 5L), r.since(a.last().at, 3).map { it.at })
    }
}
