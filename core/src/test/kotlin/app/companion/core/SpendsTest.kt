package app.companion.core

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpendsTest {
    private fun at(d: Int, h: Int = 12) = LocalDateTime.of(2026, 10, d, h, 0).atZone(IST).toInstant().toEpochMilli()

    private fun spent(kind: String = "Debit", cat: String? = "food", merchant: String? = "EatClub", title: String = "EatClub", last4: String? = "4021", ms: Long = at(4), flow: String? = null) =
        Spent(kind, cat, merchant, title, last4, ms, flow)

    private fun count(rows: List<Spent>, text: String): Int {
        val q = RulePlanner(IST).plan(text, NOW).queries.single() as Query.SumSpend
        return rows.count { Spends.match(it, q.category, q.merchant, q.last4, q.start, q.end, IST) }
    }

    private val rows = listOf(
        spent(),
        spent(merchant = "Swiggy", title = "Swiggy", kind = "CardSpend", ms = at(2)),
        spent(cat = "Food", merchant = "Zomato", title = "Zomato", ms = at(1, 0)),
        spent(flow = "Self"),
        spent(flow = "Invest"),
        spent(kind = "Credit"),
        spent(cat = "shopping", merchant = "Amazon", title = "Amazon"),
        spent(ms = at(3, 23)),
        spent(ms = LocalDateTime.of(2026, 9, 30, 23, 59).atZone(IST).toInstant().toEpochMilli()),
        spent(cat = "bills", merchant = "CRED", title = "CRED"),
    )

    @Test
    fun `a food debit with no flow counts`() {
        assertTrue(Spends.counts(spent()))
        assertTrue(Spends.counts(spent(kind = "CardSpend")))
    }

    @Test
    fun `moved money and credits are excluded`() {
        assertFalse(Spends.counts(spent(flow = "Self")))
        assertFalse(Spends.counts(spent(flow = "CardBill")))
        assertFalse(Spends.counts(spent(flow = "Invest")))
        assertFalse(Spends.counts(spent(kind = "Credit")))
        assertFalse(Spends.counts(spent(cat = "bills", merchant = "CRED", title = "CRED")))
    }

    @Test
    fun `categories match without case`() {
        assertEquals(4, count(rows, "how much did I spend on food this month"))
        assertEquals(4, count(rows, "food spend this month"))
    }

    @Test
    fun `this month includes the first and today but not last month`() {
        val q = RulePlanner(IST).plan("food this month", NOW).queries.single() as Query.SumSpend
        fun hit(ms: Long) = Spends.match(spent(ms = ms), q.category, null, null, q.start, q.end, IST)
        assertTrue(hit(at(1, 0)))
        assertTrue(hit(at(4, 23)))
        assertFalse(hit(at(5, 0)))
        assertFalse(hit(LocalDateTime.of(2026, 9, 30, 23, 59).atZone(IST).toInstant().toEpochMilli()))
    }

    @Test
    fun `merchant and week phrasings`() {
        assertEquals(1, count(rows, "swiggy this month"))
        assertEquals(1, count(rows, "swiggy this week"))
        assertEquals(2, count(rows, "eatclub this month"))
        assertEquals(6, count(rows, "how much did I spend this week"))
    }
}
