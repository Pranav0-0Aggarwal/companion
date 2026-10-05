package app.companion.core

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val day = LocalDate.of(2026, 10, 9)
    private val dayMs = 86_400_000L
    private fun want(k: String, title: String = "t") = Want(k, title, 1000, 2000, false)

    @Test
    fun `a bill is an all day event on its due date in UTC`() {
        val w = Agenda.bill("b:1", "HDFC card bill", "₹18,240", day)
        assertEquals("HDFC card bill ₹18,240 due", w.title)
        assertTrue(w.allDay)
        assertEquals(day.toEpochDay() * dayMs, w.start)
        assertEquals(w.start + dayMs, w.end)
        assertEquals("Water bill due", Agenda.bill("b:2", "Water bill", null, day).title)
    }

    @Test
    fun `a trip keeps its local date when all day and its time otherwise`() {
        val start = day.atStartOfDay(ist).toInstant().toEpochMilli()
        val all = Agenda.trip("t:1", Suggestion.Cal("Flight booking", start, start + dayMs, true, "Ref ABC123"), ist)
        assertEquals(day.toEpochDay() * dayMs, all.start)
        assertEquals(all.start + dayMs, all.end)
        assertEquals("Ref ABC123", all.note)
        val timed = Agenda.trip("t:2", Suggestion.Cal("Train booking", start + 3_600_000L, start + 7_200_000L, false, ""), ist)
        assertFalse(timed.allDay)
        assertEquals(start + 3_600_000L, timed.start)
    }

    @Test
    fun `syncing twice changes nothing`() {
        val w = listOf(want("a"), want("b"))
        val have = w.mapIndexed { i, x -> Have(i + 1L, x) }
        assertEquals(Edits(emptyList(), emptyList(), emptyList()), Agenda.diff(have, w))
    }

    @Test
    fun `new keys are added, edited ones changed and missing ones dropped`() {
        val have = listOf(Have(1, want("a")), Have(2, want("b", "old")), Have(3, want("gone")))
        val e = Agenda.diff(have, listOf(want("a"), want("b", "new"), want("c")))
        assertEquals(listOf(want("c")), e.add)
        assertEquals(listOf(Have(2, want("b", "new"))), e.change)
        assertEquals(listOf(3L), e.drop)
    }

    @Test
    fun `duplicate rows for one key keep the first and drop the rest`() {
        val e = Agenda.diff(listOf(Have(1, want("a")), Have(2, want("a")), Have(3, want("x")), Have(4, want("x"))), listOf(want("a")))
        assertTrue(e.add.isEmpty() && e.change.isEmpty())
        assertEquals(listOf(2L, 3L, 4L), e.drop.sorted())
    }

    @Test
    fun `an empty want clears everything`() {
        assertEquals(listOf(1L, 2L), Agenda.diff(listOf(Have(1, want("a")), Have(2, want("b"))), emptyList()).drop)
    }
}
