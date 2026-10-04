package app.companion.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ToolsTest {
    private val today = LocalDate.of(2026, 10, 4)
    private fun router() = Router(today, IST)

    @Test
    fun `ranges`() {
        assertEquals(Range(day(2026, 10, 1), today), Range.of("month", today))
        assertEquals(Range(day(2026, 9, 1), day(2026, 9, 30)), Range.of("last_month", today))
        assertEquals(Range(day(2026, 9, 1), day(2026, 9, 15)), Range.of("2026-09-01..2026-09-15", today))
        assertNull(Range.of("2026-09-15..2026-09-01", today))
        assertNull(Range.of("forever", today))
    }

    @Test
    fun `reads execute`() {
        val o = router().handle("sumSpend", mapOf("category" to "Food", "range" to "month", "card" to "4417"))
        val c = assertIs<Call.Spend>(assertIs<Outcome.Read>(o).call)
        assertEquals("food", c.category)
        assertEquals("4417", c.card)
    }

    @Test
    fun `search requires a query and defaults to the month`() {
        assertIs<Outcome.Reject>(router().handle("searchItems", mapOf("query" to "  ")))
        val c = assertIs<Call.Search>(assertIs<Outcome.Read>(router().handle("searchItems", mapOf("query" to "uber"))).call)
        assertEquals(Range.of("month", today), c.range)
    }

    @Test
    fun `validation rejects bad arguments`() {
        assertIs<Outcome.Reject>(router().handle("sumSpend", mapOf("category" to "gold")))
        assertIs<Outcome.Reject>(router().handle("sumSpend", mapOf("card" to "123456789012")))
        assertIs<Outcome.Reject>(router().handle("listBills", mapOf("range" to "someday")))
        assertIs<Outcome.Reject>(router().handle("dropTable", emptyMap()))
        assertIs<Outcome.Reject>(router().handle("searchItems", mapOf("query" to "x".repeat(81))))
    }

    @Test
    fun `writes are only ever proposals`() {
        val o = router().handle("createReminder", mapOf("title" to "Pay rent", "at" to "2026-10-05T09:00"))
        val p = assertIs<Outcome.Propose>(o)
        assertEquals(false, p.tainted)
        assertIs<Call.Remind>(p.call)
    }

    @Test
    fun `writes after reading content are marked tainted`() {
        val r = router()
        r.seen()
        val p = assertIs<Outcome.Propose>(r.handle("createEvent", mapOf("title" to "Dinner", "start" to "2026-10-05T20:00", "end" to "2026-10-05T21:00")))
        assertEquals(true, p.tainted)
    }

    @Test
    fun `event end must follow start and times must parse`() {
        assertIs<Outcome.Reject>(router().handle("createEvent", mapOf("title" to "A", "start" to "2026-10-05T20:00", "end" to "2026-10-05T19:00")))
        assertIs<Outcome.Reject>(router().handle("createReminder", mapOf("title" to "A", "at" to "tomorrow")))
        assertIs<Outcome.Reject>(router().handle("createReminder", mapOf("title" to "A", "at" to 5)))
    }

    @Test
    fun `tasks need no arguments`() {
        assertIs<Outcome.Read>(router().handle("listTasks", emptyMap()))
    }
}
