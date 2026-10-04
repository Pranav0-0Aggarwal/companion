package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SuggestTest {
    @Test
    fun `bill gets reminders two days before and on the day`() {
        val s = Suggest.bill("HDFC Bank card bill", day(2026, 10, 9), NOW, IST)
        assertEquals(2, s.size)
        assertEquals(Slot(day(2026, 10, 7), java.time.LocalTime.of(9, 0)).millis(IST), s[0].at)
        assertEquals(Slot(day(2026, 10, 9), java.time.LocalTime.of(9, 0)).millis(IST), s[1].at)
    }

    @Test
    fun `past reminder times are dropped`() {
        val s = Suggest.bill("Airtel bill", day(2026, 10, 5), NOW, IST)
        assertEquals(1, s.size)
    }

    @Test
    fun `train email becomes an all day event with the pnr`() {
        val text = "Your IRCTC ticket PNR 4521896532 is confirmed for 12 Oct 2026, train 12951."
        val e = assertIs<Event.Travel>(extract(sms("IRCTC", text)))
        val c = assertNotNullCal(Suggest.travel(e, text, NOW, IST))
        assertTrue(c.allDay)
        assertEquals("Ref 4521896532", c.note)
        assertEquals("Train booking", c.title)
    }

    @Test
    fun `flight with a time is timed`() {
        val text = "Your flight 6E 2145 DEL to BLR on 15 Oct at 6:45 pm is confirmed. Booking ref: ABC123."
        val e = assertIs<Event.Travel>(extract(mail("noreply@goindigo.in", "Booking confirmed", text)))
        val c = assertNotNullCal(Suggest.travel(e, text, NOW, IST))
        assertEquals(false, c.allDay)
        assertEquals(Slot(day(2026, 10, 15), java.time.LocalTime.of(18, 45)).millis(IST), c.start)
        assertEquals("Ref ABC123", c.note)
    }

    @Test
    fun `chat plan becomes an event`() {
        val c = assertNotNullCal(Suggest.chat("Ravi", "Saturday 6pm?", NOW, IST))
        assertEquals("Plan with Ravi", c.title)
        assertEquals(Slot(day(2026, 10, 10), java.time.LocalTime.of(18, 0)).millis(IST), c.start)
    }

    @Test
    fun `chat without a time suggests nothing`() {
        assertNull(Suggest.chat("Ravi", "see you saturday", NOW, IST))
        assertNull(Suggest.chat("Ravi", "lol", NOW, IST))
    }

    @Test
    fun `shared text routes to todo reminder or event`() {
        assertIs<Suggestion.Todo>(Suggest.shared("Buy milk", NOW, IST))
        assertIs<Suggestion.Remind>(Suggest.shared("Pay rent on 12 Oct", NOW, IST))
        assertIs<Suggestion.Cal>(Suggest.shared("Team dinner tomorrow 8pm", NOW, IST))
    }

    private fun assertNotNullCal(c: Suggestion.Cal?): Suggestion.Cal = c ?: error("no event")
}
