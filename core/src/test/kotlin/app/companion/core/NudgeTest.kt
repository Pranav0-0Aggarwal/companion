package app.companion.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NudgeTest {
    private val z = ZoneId.of("Asia/Kolkata")
    private val day = LocalDate.of(2026, 10, 5)
    private fun at(h: Int, m: Int = 0, d: LocalDate = day) = d.atTime(h, m).atZone(z).toInstant().toEpochMilli()
    private val times = MealTimes.defaults
    private val all = Eat.entries.toSet()

    private fun due(now: Long, logged: Set<Eat> = emptySet(), paid: List<Long> = emptyList(), nudged: Set<Eat> = emptySet(), on: Set<Eat> = all, quiet: Quiet = Quiet.default) =
        Nudge.due(now, z, times, logged, paid, nudged, on, quiet)

    private fun next(now: Long, logged: Set<Eat> = emptySet(), paid: List<Long> = emptyList(), nudged: Set<Eat> = emptySet(), on: Set<Eat> = all, quiet: Quiet = Quiet.default) =
        Nudge.next(now, z, times, logged, paid, nudged, on, quiet)

    @Test
    fun `defaults without enough samples`() {
        val s = listOf(Eat.Lunch to at(12, 0, day.minusDays(1)), Eat.Lunch to at(12, 0, day.minusDays(2)))
        assertEquals(MealTimes.defaults, MealTimes.learn(s, at(10), z))
    }

    @Test
    fun `median of the last 21 days per slot`() {
        val s = listOf(
            Eat.Lunch to at(12, 0, day.minusDays(1)), Eat.Lunch to at(12, 30, day.minusDays(2)), Eat.Lunch to at(14, 0, day.minusDays(3)),
            Eat.Lunch to at(23, 0, day.minusDays(30)),
        )
        val m = MealTimes.learn(s, at(10), z)
        assertEquals(LocalTime.of(12, 30), m.getValue(Eat.Lunch))
        assertEquals(LocalTime.of(21, 0), m.getValue(Eat.Dinner))
    }

    @Test
    fun `even sample counts average the middle pair`() {
        val s = listOf(0, 1, 2, 3).map { Eat.Dinner to at(20, it * 10, day.minusDays(it + 1L)) }
        assertEquals(LocalTime.of(20, 15), MealTimes.learn(s, at(10), z).getValue(Eat.Dinner))
    }

    @Test
    fun `learned times stay inside the slot band`() {
        val early = (1..4).map { Eat.Dinner to at(15, 0, day.minusDays(it.toLong())) }
        val late = (1..4).map { Eat.Breakfast to at(14, 0, day.minusDays(it.toLong())) }
        val m = MealTimes.learn(early + late, at(10), z)
        assertEquals(LocalTime.of(18, 30), m.getValue(Eat.Dinner))
        assertEquals(LocalTime.of(11, 0), m.getValue(Eat.Breakfast))
    }

    @Test
    fun `samples in the future are ignored`() {
        val s = (1..4).map { Eat.Snacks to at(16, 0, day.plusDays(it.toLong())) }
        assertEquals(LocalTime.of(17, 30), MealTimes.learn(s, at(10), z).getValue(Eat.Snacks))
    }

    @Test
    fun `quiet hours wrap past midnight`() {
        val q = Quiet.default
        assertTrue(q.has(LocalTime.of(23, 0)))
        assertTrue(q.has(LocalTime.of(2, 0)))
        assertTrue(q.has(LocalTime.of(22, 30)))
        assertTrue(!q.has(LocalTime.of(7, 0)))
        assertTrue(!q.has(LocalTime.of(12, 0)))
        val day = Quiet(LocalTime.of(13, 0), LocalTime.of(15, 0))
        assertTrue(day.has(LocalTime.of(14, 0)))
        assertTrue(!day.has(LocalTime.of(16, 0)))
        assertTrue(!Quiet(LocalTime.of(8, 0), LocalTime.of(8, 0)).has(LocalTime.of(8, 0)))
    }

    @Test
    fun `quiet clear moves to the end`() {
        val q = Quiet.default
        assertEquals(at(7, 0, day.plusDays(1)), q.clear(day.atTime(23, 0).atZone(z)).toInstant().toEpochMilli())
        assertEquals(at(7, 0), q.clear(day.atTime(3, 0).atZone(z)).toInstant().toEpochMilli())
        assertEquals(at(12, 0), q.clear(day.atTime(12, 0).atZone(z)).toInstant().toEpochMilli())
    }

    @Test
    fun `nudges an hour after the usual time`() {
        assertNull(due(at(14, 29)))
        assertEquals(Eat.Lunch, due(at(14, 30)))
        assertEquals(Eat.Lunch, due(at(18, 29)))
        assertNull(due(at(18, 30, day), logged = emptySet(), on = setOf(Eat.Lunch)))
    }

    @Test
    fun `earliest eligible slot wins`() {
        assertEquals(Eat.Breakfast, due(at(10, 30)))
        assertEquals(Eat.Lunch, due(at(14, 45), logged = setOf(Eat.Breakfast)))
        assertEquals(Eat.Snacks, due(at(18, 45), logged = setOf(Eat.Lunch)))
    }

    @Test
    fun `logged nudged and disabled slots are skipped`() {
        assertNull(due(at(14, 45), logged = setOf(Eat.Lunch), on = setOf(Eat.Lunch)))
        assertNull(due(at(14, 45), nudged = setOf(Eat.Lunch), on = setOf(Eat.Lunch)))
        assertNull(due(at(14, 45), on = setOf(Eat.Dinner)))
        assertNull(due(at(14, 45), on = emptySet()))
    }

    @Test
    fun `a food payment around the usual time suppresses`() {
        assertNull(due(at(15, 0), paid = listOf(at(13, 0)), on = setOf(Eat.Lunch)))
        assertNull(due(at(15, 0), paid = listOf(at(14, 50)), on = setOf(Eat.Lunch)))
        assertEquals(Eat.Lunch, due(at(15, 0), paid = listOf(at(12, 29)), on = setOf(Eat.Lunch)))
        assertEquals(Eat.Lunch, due(at(15, 0), paid = listOf(at(15, 30)), on = setOf(Eat.Lunch)))
    }

    @Test
    fun `quiet hours hold the nudge`() {
        val late = mapOf(Eat.Dinner to LocalTime.of(22, 0))
        assertNull(Nudge.due(at(23, 0), z, late, emptySet(), emptyList(), emptySet(), setOf(Eat.Dinner)))
        assertEquals(Eat.Dinner, Nudge.due(at(23, 0), z, late, emptySet(), emptyList(), emptySet(), setOf(Eat.Dinner), Quiet(LocalTime.of(1, 0), LocalTime.of(2, 0))))
    }

    @Test
    fun `missing slot times fall back to defaults`() {
        assertEquals(Eat.Lunch, Nudge.due(at(14, 30), z, emptyMap(), emptySet(), emptyList(), emptySet(), all))
    }

    @Test
    fun `next is the start of the earliest remaining window`() {
        assertEquals(at(14, 30), next(at(10, 0), logged = setOf(Eat.Breakfast)))
        assertEquals(at(10, 0), next(at(8, 0)))
        assertEquals(at(14, 30), next(at(14, 30), logged = setOf(Eat.Breakfast)))
        assertEquals(at(15, 0), next(at(15, 0), logged = setOf(Eat.Breakfast)))
        assertEquals(at(18, 30), next(at(14, 0), logged = setOf(Eat.Breakfast, Eat.Lunch)))
    }

    @Test
    fun `next skips handled slots and paid windows`() {
        assertEquals(at(18, 30), next(at(13, 0), logged = setOf(Eat.Breakfast), nudged = setOf(Eat.Lunch)))
        assertEquals(at(18, 30), next(at(15, 0), logged = setOf(Eat.Breakfast), paid = listOf(at(14, 0))))
        assertEquals(at(22, 0), next(at(19, 0), logged = setOf(Eat.Breakfast, Eat.Lunch, Eat.Snacks)))
    }

    @Test
    fun `next rolls to tomorrow when the day is spent`() {
        assertEquals(at(10, 0, day.plusDays(1)), next(at(22, 5), logged = all))
        assertEquals(at(10, 0, day.plusDays(1)), next(at(23, 0), on = all))
        assertEquals(at(22, 0, day.plusDays(1)), next(at(23, 0), on = setOf(Eat.Dinner), nudged = setOf(Eat.Dinner)))
        assertNull(next(at(23, 0), on = emptySet()))
    }

    @Test
    fun `next moves past quiet hours or drops the slot`() {
        val late = mapOf(Eat.Dinner to LocalTime.of(21, 45))
        val a = Nudge.next(at(20, 0), z, late, emptySet(), emptyList(), emptySet(), setOf(Eat.Dinner))
        assertNull(a)
        val q = Quiet(LocalTime.of(10, 0), LocalTime.of(11, 0))
        assertEquals(at(11, 0), Nudge.next(at(8, 0), z, times, emptySet(), emptyList(), emptySet(), setOf(Eat.Breakfast), q))
        val tight = Quiet(LocalTime.of(10, 0), LocalTime.of(15, 0))
        assertNull(Nudge.next(at(8, 0), z, times, emptySet(), emptyList(), emptySet(), setOf(Eat.Breakfast), tight))
    }

    @Test
    fun `next agrees with due`() {
        val n = assertNotNull(next(at(8, 0)))
        assertEquals(Eat.Breakfast, due(n))
    }

    @Test
    fun `spend prompt for cafes outside the brand db`() {
        assertEquals("Logged ₹240 at Third Wave. What did you have?", Spend.prompt("Third Wave", "food", 24000, false, false))
        assertEquals("Logged ₹1,24,500.50 at Corner Cafe. What did you have?", Spend.prompt("Corner Cafe", "food", 12450050, false, false))
        assertEquals("Logged ₹99 at Corner Cafe. What did you have?", Spend.prompt(" Corner Cafe ", "food", 9900, false, false))
        assertEquals("Logged ₹0.50 at X. What did you have?", Spend.prompt("X", "food", 50, false, false))
    }

    @Test
    fun `spend prompt skips items brands and other categories`() {
        assertNull(Spend.prompt("Third Wave", "food", 24000, true, false))
        assertNull(Spend.prompt("Starbucks", "food", 24000, false, true))
        assertNull(Spend.prompt("Reliance Fresh", "groceries", 24000, false, false))
        assertNull(Spend.prompt("Third Wave", null, 24000, false, false))
        assertNull(Spend.prompt(null, "food", 24000, false, false))
        assertNull(Spend.prompt("  ", "food", 24000, false, false))
        assertNull(Spend.prompt("Third Wave", "food", 0, false, false))
    }

    private val sun = LocalDate.of(2026, 10, 4)

    @Test
    fun `weight nudge fires on the day after the time`() {
        assertTrue(WeightNudge.due(at(9, 0, sun), z, null, false))
        assertTrue(WeightNudge.due(at(18, 0, sun), z, null, false))
        assertTrue(!WeightNudge.due(at(8, 59, sun), z, null, false))
        assertTrue(!WeightNudge.due(at(10, 0, day), z, null, false))
        assertTrue(!WeightNudge.due(at(10, 0, sun), z, null, true))
        assertTrue(!WeightNudge.due(at(23, 0, sun), z, null, false))
        assertTrue(WeightNudge.due(at(10, 0, day), z, null, false, LocalTime.of(9, 0), DayOfWeek.MONDAY))
    }

    @Test
    fun `weight nudge skips a recent weigh in`() {
        assertTrue(!WeightNudge.due(at(10, 0, sun), z, at(7, 0, sun), false))
        assertTrue(!WeightNudge.due(at(10, 0, sun), z, at(7, 0, sun.minusDays(2)), false))
        assertTrue(WeightNudge.due(at(10, 0, sun), z, at(7, 0, sun.minusDays(3)), false))
    }

    @Test
    fun `next weight prompt is the coming sunday`() {
        assertEquals(at(9, 0, sun), WeightNudge.next(at(12, 0, sun.minusDays(2)), z))
        assertEquals(at(9, 0, sun.plusDays(7)), WeightNudge.next(at(9, 0, sun), z))
        assertEquals(at(9, 0, sun), WeightNudge.next(at(8, 0, sun), z))
        assertEquals(at(7, 0, sun), WeightNudge.next(at(1, 0, sun), z, LocalTime.of(6, 0)))
    }

    @Test
    fun `trend is a least squares slope with a seven day average`() {
        val p = (0..6).map { sun.minusDays(7L * (6 - it)) to 80.0 - it * 0.5 }
        val t = assertNotNull(WeightNudge.trend(p))
        assertEquals(-0.5, t.slopePerWeek, 1e-9)
        assertEquals(77.0, t.latest, 1e-9)
        assertEquals(77.0, t.avg7, 1e-9)
    }

    @Test
    fun `trend averages the last seven days and ignores old points`() {
        val p = listOf(sun.minusDays(100) to 120.0, sun.minusDays(8) to 72.0, sun.minusDays(6) to 71.0, sun.minusDays(2) to 70.0, sun to 69.0)
        val t = assertNotNull(WeightNudge.trend(p))
        assertEquals(70.0, t.avg7, 1e-9)
        assertEquals(69.0, t.latest, 1e-9)
        assertTrue(t.slopePerWeek < 0 && t.slopePerWeek > -3)
    }

    @Test
    fun `trend needs two points`() {
        assertNull(WeightNudge.trend(emptyList()))
        assertNull(WeightNudge.trend(listOf(sun to 70.0)))
        assertNull(WeightNudge.trend(listOf(sun.minusDays(100) to 80.0, sun to 70.0)))
        assertEquals(0.0, assertNotNull(WeightNudge.trend(listOf(sun to 70.0, sun to 72.0))).slopePerWeek)
    }

    @Test
    fun `kg replies parse in common shapes`() {
        assertEquals(72.4, WeightNudge.parseKg("72.4"))
        assertEquals(72.4, WeightNudge.parseKg("72,4"))
        assertEquals(72.0, WeightNudge.parseKg("72 kg"))
        assertEquals(72.4, WeightNudge.parseKg("72.4kg"))
        assertEquals(72.4, WeightNudge.parseKg("  72.4 KGS. "))
        assertEquals(101.0, WeightNudge.parseKg("101"))
    }

    @Test
    fun `kg replies outside the range or with words are rejected`() {
        listOf("24.9", "301", "1000", "seventy", "72 lbs", "", "kg", "72.4.1", "ate 72", "-72", "5").forEach { assertNull(WeightNudge.parseKg(it), it) }
        assertEquals(25.0, WeightNudge.parseKg("25"))
        assertEquals(300.0, WeightNudge.parseKg("300"))
    }
}
