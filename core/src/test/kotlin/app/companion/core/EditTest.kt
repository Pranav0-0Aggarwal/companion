package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EditTest {
    private val e = Edit(NOW, IST)
    private val sum = Query.SumSpend("food", null, null, day(2026, 9, 1), day(2026, 9, 30))

    @Test
    fun `values round trip to the same query`() {
        val qs = listOf(
            sum,
            Query.ListTxns("food", "Swiggy", "4417", day(2026, 9, 1), day(2026, 9, 30), 10),
            Query.ListBills(day(2026, 10, 4), day(2026, 10, 31), true),
            Query.TopMerchants(day(2026, 9, 1), day(2026, 9, 30), 5),
            Query.CreateReminder("Pay rent", NOW + 3_600_000),
            Query.CreateEvent("Dinner", NOW + 3_600_000, NOW + 7_200_000),
        )
        qs.forEach {
            assertEquals(e.labels(it).size, e.values(it).size)
            assertEquals(it.javaClass, e.apply(it, e.values(it))?.javaClass)
        }
        assertEquals(sum, e.apply(sum, e.values(sum)))
    }

    @Test
    fun `fields change the query and blanks clear filters`() {
        val q = e.apply(sum, listOf("", "Swiggy", "", "2026-08-01", "2026-08-31")) as Query.SumSpend
        assertEquals(null, q.category)
        assertEquals("Swiggy", q.merchant)
        assertEquals(day(2026, 8, 1), q.start)
    }

    @Test
    fun `bad input is refused`() {
        assertNull(e.apply(sum, listOf("gold", "", "", "2026-09-01", "2026-09-30")))
        assertNull(e.apply(sum, listOf("food", "", "", "2026-09-31", "2026-09-30")))
        assertNull(e.apply(sum, listOf("food", "", "", "2026-09-30", "2026-09-01")))
        assertNull(e.apply(sum, listOf("food", "", "12", "2026-09-01", "2026-09-30")))
        assertNull(e.apply(Query.CreateReminder("x", NOW + 1000), listOf("x", "tomorrow")))
    }
}
