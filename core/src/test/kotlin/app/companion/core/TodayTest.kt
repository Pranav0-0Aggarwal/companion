package app.companion.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TodayTest {
    private val d = LocalDate.of(2026, 3, 10)

    @Test
    fun `safe to spend divides what is left by the days to payday`() {
        assertEquals(Margin.Safe(1_000L), Safe.room(100_000, 30_000, 10_000, 60))
        assertEquals(Margin.Safe(600L), Safe.room(100_000, 30_000, 10_000, 100))
        assertEquals(Margin.Safe(500L), Safe.room(100_000, 30_000, 10_000, 120))
    }

    @Test
    fun `over budget reports the shortfall including bills before payday`() {
        assertEquals(Margin.Over(10_000L), Safe.room(10_000, 20_000, 0, 5))
        assertEquals(Margin.Over(5_000L), Safe.room(100_000, 90_000, 15_000, 5))
        assertEquals(Margin.Over(0L), Safe.room(10_000, 10_000, 0, 5))
        assertEquals(Margin.Safe(100L), Safe.room(10_000, 9_500, 0, 5))
    }

    @Test
    fun `no margin without a budget or payday`() {
        assertNull(Safe.room(null, 0, 0, 5))
        assertNull(Safe.room(0, 0, 0, 5))
        assertNull(Safe.room(10_000, 0, 0, null))
    }

    @Test
    fun `payday today still counts one day`() {
        assertEquals(1, Safe.days(d, d))
        assertEquals(5, Safe.days(d, d.plusDays(5)))
        assertEquals(Margin.Safe(500L), Safe.room(500, 0, 0, Safe.days(d, d)))
    }

    @Test
    fun `the needs line counts asks and bills separately`() {
        assertEquals("nothing needs you", Needs.line(0, 0))
        assertEquals("1 needs you", Needs.line(1, 0))
        assertEquals("142 need you · 1 bill due", Needs.line(142, 1))
        assertEquals("2 bills due", Needs.line(0, 2))
    }

    @Test
    fun `only bills due before payday and with a date are set aside`() {
        val dues = listOf<Pair<LocalDate?, Long>>(d.minusDays(2) to 100, d.plusDays(4) to 200, d.plusDays(5) to 400, null to 800)
        assertEquals(300L, Safe.owed(dues, d.plusDays(5)))
    }

    @Test
    fun `the strip centres seven days on the selected one`() {
        val s = Strip.days(d)
        assertEquals(7, s.size)
        assertEquals(d.minusDays(3), s.first())
        assertEquals(d, s[3])
        assertEquals(d.plusDays(3), s.last())
    }

    private class E(val k: String, val at: Long?, val r: Int = 0)

    private val at = { e: E -> e.at }

    @Test
    fun `all day items lead then the rest follow in time order and rank breaks ties`() {
        val l = listOf(E("c", 30), E("a", null), E("b", 10), E("d", 30, -1))
        assertEquals(listOf("a", "b", "d", "c"), Timeline.order(l, at) { it.r }.map { it.k })
    }

    @Test
    fun `now sits before the first later item`() {
        val l = Timeline.order(listOf(E("a", null), E("b", 10), E("c", 20), E("d", 40)), at)
        assertEquals(3, Timeline.nowAt(l, at, 25))
        assertEquals(1, Timeline.nowAt(l, at, 5))
        assertEquals(4, Timeline.nowAt(l, at, 99))
    }

    @Test
    fun `around keeps the last past item and the next ones`() {
        val l = Timeline.order((1..8).map { E("e$it", it * 10L) }, at)
        assertEquals(listOf("e4", "e5", "e6", "e7"), Timeline.around(l, at, 45, 4).map { it.k })
        assertEquals(listOf("e5", "e6", "e7", "e8"), Timeline.around(l, at, 99, 4).map { it.k })
        assertEquals(listOf("e1", "e2", "e3", "e4"), Timeline.around(l, at, 0, 4).map { it.k })
        assertEquals(2, Timeline.around(l.take(2), at, 15, 4).size)
    }

    @Test
    fun `the privacy line counts messages and always says nothing was sent`() {
        assertEquals("Read nothing today · sent nothing", Privacy.line(0))
        assertEquals("Read 1 message today · sent nothing", Privacy.line(1))
        assertEquals("Read 42 messages today · sent nothing", Privacy.line(42))
    }

    @Test
    fun `the weight window keeps four weeks in order`() {
        val p = listOf(d to 71.0, d.minusDays(27) to 72.0, d.minusDays(28) to 73.0, d.plusDays(1) to 70.0, d.minusDays(10) to 71.5)
        assertEquals(listOf(72.0, 71.5, 71.0), Spark.window(p, d).map { it.second })
    }
}
