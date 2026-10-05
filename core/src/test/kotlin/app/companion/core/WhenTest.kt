package app.companion.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WhenTest {
    private fun ms(d: Int, h: Int, m: Int = 0, month: Int = 10) = LocalDateTime.of(2026, month, d, h, m).atZone(IST).toInstant().toEpochMilli()

    private val mon = ms(5, 15, 30)

    private fun w(text: String, now: Long = mon) = When.resolve(text, now, IST)

    private fun check(text: String, day: LocalDate, meal: Meal, at: Long, now: Long = mon) =
        assertEquals(Wen(day, meal, at), w(text, now), text)

    private val oct4 = day(2026, 10, 4)
    private val oct5 = day(2026, 10, 5)
    private val oct3 = day(2026, 10, 3)

    @Test
    fun `yesterday and last night are the previous day`() {
        check("yesterday dinner", oct4, Meal.Dinner, ms(4, 21))
        check("Yesterday Lunch", oct4, Meal.Lunch, ms(4, 13, 30))
        check("last night", oct4, Meal.Dinner, ms(4, 21))
        check("last evening", oct4, Meal.Snacks, ms(4, 17, 30))
        check("yesterday morning", oct4, Meal.Breakfast, ms(4, 9))
        check("yesterday", oct4, Meal.Snacks, ms(4, 17, 30))
    }

    @Test
    fun `today phrases`() {
        check("this morning", oct5, Meal.Breakfast, ms(5, 9))
        check("today lunch", oct5, Meal.Lunch, ms(5, 13, 30))
        check("this afternoon", oct5, Meal.Lunch, ms(5, 13, 30))
        check("today", oct5, Meal.Snacks, ms(5, 17, 30).coerceAtMost(mon))
        check("tonight", oct5, Meal.Dinner, mon)
    }

    @Test
    fun `a morning stays today even in the evening`() {
        val eve = ms(5, 21, 45)
        check("this morning", oct5, Meal.Breakfast, ms(5, 9), eve)
        check("tonight", oct5, Meal.Dinner, ms(5, 21), eve)
        check("breakfast", oct5, Meal.Breakfast, ms(5, 9), eve)
    }

    @Test
    fun `weekdays look back`() {
        check("last Sunday lunch", oct4, Meal.Lunch, ms(4, 13, 30))
        check("last Monday lunch", day(2026, 9, 28), Meal.Lunch, ms(28, 13, 30, 9))
        check("on Monday breakfast", oct5, Meal.Breakfast, ms(5, 9))
        check("on Friday dinner", day(2026, 10, 2), Meal.Dinner, ms(2, 21))
        check("saturday lunch", oct3, Meal.Lunch, ms(3, 13, 30))
        check("last sunday", day(2026, 9, 27), Meal.Lunch, ms(27, 13, 30, 9), ms(4, 13, 30))
        check("ravivar raat", oct4, Meal.Dinner, ms(4, 21))
    }

    @Test
    fun `days ago`() {
        check("2 days ago", oct3, Meal.Snacks, ms(3, 17, 30))
        check("2 days ago dinner", oct3, Meal.Dinner, ms(3, 21))
        check("day before yesterday", oct3, Meal.Snacks, ms(3, 17, 30))
        check("parso lunch", oct3, Meal.Lunch, ms(3, 13, 30))
        check("teen din pehle breakfast", day(2026, 10, 2), Meal.Breakfast, ms(2, 9))
        check("a day ago lunch", oct4, Meal.Lunch, ms(4, 13, 30))
    }

    @Test
    fun `Hinglish phrases`() {
        check("kal raat ko", oct4, Meal.Dinner, ms(4, 21))
        check("aaj subah", oct5, Meal.Breakfast, ms(5, 9))
        check("kal subah nashta", oct4, Meal.Breakfast, ms(4, 9))
        check("aaj dopahar", oct5, Meal.Lunch, ms(5, 13, 30))
        check("kal shaam", oct4, Meal.Snacks, ms(4, 17, 30))
    }

    @Test
    fun `now and relative durations`() {
        check("abhi", oct5, Meal.Snacks, mon)
        check("just now", oct5, Meal.Snacks, mon)
        check("right now", oct5, Meal.Snacks, mon)
        check("2 hours ago", oct5, Meal.Lunch, ms(5, 13, 30))
        check("30 minutes ago", oct5, Meal.Snacks, ms(5, 15))
        check("half an hour ago", oct5, Meal.Snacks, ms(5, 15))
        check("ek ghante pehle", oct5, Meal.Lunch, ms(5, 14, 30))
        check("", oct5, Meal.Snacks, mon)
    }

    @Test
    fun `explicit clock times`() {
        check("at 1pm", oct5, Meal.Lunch, ms(5, 13))
        check("lunch at 1pm", oct5, Meal.Lunch, ms(5, 13))
        check("yesterday 8:30 pm", oct4, Meal.Dinner, ms(4, 20, 30))
        check("yesterday 20:15", oct4, Meal.Dinner, ms(4, 20, 15))
        check("yesterday breakfast at 10", oct4, Meal.Breakfast, ms(4, 10))
        check("yesterday dinner at 8", oct4, Meal.Dinner, ms(4, 20))
        check("8:30", oct5, Meal.Breakfast, ms(5, 8, 30))
        check("8 baje raat", oct4, Meal.Dinner, ms(4, 20))
        check("today at 9 pm", oct5, Meal.Dinner, mon)
    }

    @Test
    fun `an explicit time later today rolls back to yesterday`() {
        check("at 8pm", oct4, Meal.Dinner, ms(4, 20))
        check("at 9:15 pm", oct4, Meal.Dinner, ms(4, 21, 15))
    }

    @Test
    fun `an ambiguous time on a past day follows the usual meal hours`() {
        check("yesterday 7:30", oct4, Meal.Breakfast, ms(4, 7, 30))
        check("yesterday 1:30", oct4, Meal.Lunch, ms(4, 13, 30))
        check("yesterday 12:15", oct4, Meal.Lunch, ms(4, 12, 15))
    }

    @Test
    fun `just after midnight last night is the previous day`() {
        val night = ms(5, 0, 30)
        check("last night", oct4, Meal.Dinner, ms(4, 21), night)
        check("yesterday dinner", oct4, Meal.Dinner, ms(4, 21), night)
        check("tonight", oct4, Meal.Dinner, ms(4, 21), night)
        check("dinner", oct4, Meal.Dinner, ms(4, 21), night)
        check("this morning", oct5, Meal.Breakfast, night, night)
        check("just now", oct5, Meal.Dinner, night, night)
        check("last sunday", oct4, Meal.Dinner, ms(4, 21), night)
        check("at 11pm", oct4, Meal.Dinner, ms(4, 23), night)
    }

    @Test
    fun `future instants clamp to now`() {
        check("today dinner", oct5, Meal.Dinner, mon)
        check("this evening", oct5, Meal.Snacks, ms(5, 17, 30).coerceAtMost(mon))
        val early = ms(5, 8)
        check("today lunch", oct5, Meal.Lunch, early, early)
    }

    @Test
    fun `custom meal times apply`() {
        val times = Meals.defaults + (Meal.Dinner to LocalTime.of(20, 0))
        assertEquals(Wen(oct4, Meal.Dinner, ms(4, 20)), When.resolve("yesterday dinner", mon, IST, times))
    }

    @Test
    fun `unknown text resolves to nothing`() {
        assertNull(w("pizza"))
        assertNull(w("two plates of rice"))
        assertNull(w("x days ago"))
    }

    @Test
    fun `meal slots classify by hour`() {
        listOf(
            3 to 59 to Meal.Dinner, 4 to 0 to Meal.Breakfast, 10 to 59 to Meal.Breakfast, 11 to 0 to Meal.Lunch, 14 to 59 to Meal.Lunch,
            15 to 0 to Meal.Snacks, 18 to 59 to Meal.Snacks, 19 to 0 to Meal.Dinner, 23 to 59 to Meal.Dinner,
        ).forEach { (hm, meal) -> assertEquals(meal, Meal.of(hm.first, hm.second), "$hm") }
        assertEquals(Meal.Lunch, Meal.of(LocalTime.of(13, 30)))
    }

    @Test
    fun `meal labels parse`() {
        listOf(
            "dinner" to Meal.Dinner, "Raat ka khana" to Meal.Dinner, "nashta" to Meal.Breakfast, "subah ka nashta" to Meal.Breakfast, "dopahar ka khana" to Meal.Lunch,
            "evening snacks" to Meal.Snacks, "morning snack" to Meal.Snacks, "Breakfast" to Meal.Breakfast, "lunch" to Meal.Lunch, "shaam" to Meal.Snacks, "tonight" to Meal.Dinner,
        ).forEach { (t, m) -> assertEquals(m, Meal.parse(t), t) }
        assertNull(Meal.parse("xyz"))
        assertEquals(listOf("breakfast", "lunch", "snacks", "dinner"), Meal.entries.map { it.label })
        assertEquals(Meal.Snacks, Meal.label("snacks"))
        assertEquals(LocalTime.of(13, 30), Meals.defaults[Meal.Lunch])
    }
}
