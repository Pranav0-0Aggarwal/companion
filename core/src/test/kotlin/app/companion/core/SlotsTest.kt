package app.companion.core

import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SlotsTest {
    private fun find(t: String) = Slots.find(t, NOW, IST)

    @Test
    fun `tomorrow with clock`() {
        assertEquals(Slot(day(2026, 10, 5), LocalTime.of(19, 0)), find("tomorrow 7pm"))
        assertEquals(Slot(day(2026, 10, 5), LocalTime.of(19, 30)), find("Lunch tmrw 7:30 pm?"))
    }

    @Test
    fun `weekday with spaced meridiem`() {
        assertEquals(Slot(day(2026, 10, 10), LocalTime.of(18, 0)), find("Sat 6 pm"))
        assertEquals(Slot(day(2026, 10, 10), LocalTime.of(18, 0)), find("Saturday 6pm?"))
    }

    @Test
    fun `same weekday later today or next week`() {
        assertEquals(Slot(day(2026, 10, 4), LocalTime.of(20, 0)), find("Sunday 8pm"))
        assertEquals(Slot(day(2026, 10, 11), LocalTime.of(10, 0)), find("Sunday 10am"))
        assertEquals(Slot(day(2026, 10, 11), LocalTime.of(10, 0)), find("next sun 10am"))
    }

    @Test
    fun `today tonight and day after`() {
        assertEquals(Slot(day(2026, 10, 4), LocalTime.of(17, 30)), find("today 5:30pm"))
        assertEquals(Slot(day(2026, 10, 4), LocalTime.of(20, 0)), find("dinner tonight"))
        assertEquals(Slot(day(2026, 10, 6), LocalTime.of(11, 0)), find("day after tomorrow 11am"))
    }

    @Test
    fun `bare hour after at uses context`() {
        assertEquals(Slot(day(2026, 10, 5), LocalTime.of(20, 0)), find("dinner tomorrow at 8"))
        assertEquals(Slot(day(2026, 10, 5), LocalTime.of(9, 0)), find("meet tomorrow at 9"))
        assertEquals(Slot(day(2026, 10, 5), LocalTime.of(15, 0)), find("tomorrow at 3"))
    }

    @Test
    fun `twenty four hour clock`() {
        assertEquals(Slot(day(2026, 10, 5), LocalTime.of(18, 45)), find("tomorrow 18:45"))
    }

    @Test
    fun `calendar dates`() {
        assertEquals(Slot(day(2026, 10, 12), LocalTime.of(18, 30)), find("on 12 Oct at 6:30pm"))
        assertEquals(Slot(day(2026, 10, 12), LocalTime.of(19, 0)), find("12/10 7pm"))
        assertEquals(Slot(day(2026, 10, 15), null), find("flight on 15 Oct"))
    }

    @Test
    fun `relative durations`() {
        assertEquals(Slot(day(2026, 10, 4), LocalTime.of(15, 30)), find("remind me in 2 hours"))
        assertEquals(Slot(day(2026, 10, 4), LocalTime.of(14, 0)), find("in 30 min"))
    }

    @Test
    fun `clock only rolls to the next occurrence`() {
        assertEquals(Slot(day(2026, 10, 4), LocalTime.of(21, 0)), find("call at 9pm"))
        assertEquals(Slot(day(2026, 10, 5), LocalTime.of(9, 0)), find("call at 9am"))
    }

    @Test
    fun `money and chatter are not times`() {
        assertNull(find("send me Rs 500 at 100% please"))
        assertNull(find("lol that was funny"))
    }

    @Test
    fun `millis in ist`() {
        assertEquals(
            java.time.LocalDateTime.of(2026, 10, 5, 19, 0).atZone(IST).toInstant().toEpochMilli(),
            find("tomorrow 7pm")!!.millis(IST),
        )
    }
}
