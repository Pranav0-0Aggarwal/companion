package app.companion.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RulePlannerTest {
    private val p = RulePlanner(IST)
    private val today = LocalDate.of(2026, 10, 4)

    private fun one(text: String) = p.plan(text, NOW).queries.single()

    private fun sum(text: String) = assertIs<Query.SumSpend>(one(text))

    @Test
    fun `category over last month`() {
        val q = sum("how much did I spend on food last month")
        assertEquals("food", q.category)
        assertEquals(day(2026, 9, 1), q.start)
        assertEquals(day(2026, 9, 30), q.end)
    }

    @Test
    fun `merchant this week runs monday to today`() {
        val q = sum("Swiggy this week")
        assertEquals("Swiggy", q.merchant)
        assertEquals(null, q.category)
        assertEquals(day(2026, 9, 28), q.start)
        assertEquals(today, q.end)
    }

    @Test
    fun `hinglish category and month`() {
        val q = sum("khana pe kitna kharcha kiya pichle mahine")
        assertEquals("food", q.category)
        assertEquals(day(2026, 9, 1), q.start)
    }

    @Test
    fun `hinglish week and merchant`() {
        val q = sum("zomato is hafte")
        assertEquals("Zomato", q.merchant)
        assertEquals(day(2026, 9, 28), q.start)
    }

    @Test
    fun `yesterday and kal`() {
        assertEquals(day(2026, 10, 3), sum("uber yesterday").start)
        assertEquals(day(2026, 10, 3), sum("uber kal").end)
    }

    @Test
    fun `past n days is rolling`() {
        val q = sum("past 10 days uber")
        assertEquals(day(2026, 9, 25), q.start)
        assertEquals(today, q.end)
        assertEquals(day(2026, 9, 28), sum("pichle 7 din swiggy").start)
    }

    @Test
    fun `named months`() {
        val q = sum("spent in September")
        assertEquals(day(2026, 9, 1), q.start)
        assertEquals(day(2026, 9, 30), q.end)
        assertEquals(day(2025, 12, 1), sum("how much in december").start)
        assertEquals(day(2025, 8, 1), sum("food august 2025").start)
        assertEquals(day(2026, 5, 1), sum("kharcha may mein").start)
    }

    @Test
    fun `default is month to date`() {
        val q = sum("how much on groceries")
        assertEquals("groceries", q.category)
        assertEquals(day(2026, 10, 1), q.start)
        assertEquals(today, q.end)
    }

    @Test
    fun `two brands of one category become the category`() {
        val q = sum("swiggy and zomato this month")
        assertEquals("food", q.category)
        assertEquals(null, q.merchant)
    }

    @Test
    fun `unknown merchant word`() {
        assertEquals("Tokai", sum("how much at tokai this month").merchant)
    }

    @Test
    fun `card last four`() {
        val q = assertIs<Query.ListTxns>(one("show transactions card 4417 this week"))
        assertEquals("4417", q.last4)
        assertEquals(10, q.limit)
    }

    @Test
    fun `list with limit`() {
        val q = assertIs<Query.ListTxns>(one("last 5 transactions on food"))
        assertEquals(5, q.limit)
        assertEquals("food", q.category)
    }

    @Test
    fun `top merchants`() {
        val q = assertIs<Query.TopMerchants>(one("top 3 merchants last month"))
        assertEquals(3, q.n)
        assertEquals(day(2026, 9, 1), q.start)
        assertEquals(5, assertIs<Query.TopMerchants>(one("sabse zyada kharcha kahan hua")).n)
    }

    @Test
    fun `bills due`() {
        val q = assertIs<Query.ListBills>(one("bills due this month"))
        assertEquals(today, q.start)
        assertEquals(day(2026, 10, 31), q.end)
        assertTrue(q.unpaidOnly)
        assertEquals(today.plusDays(30), assertIs<Query.ListBills>(one("pending bills")).end)
        assertEquals(today.plusDays(7), assertIs<Query.ListBills>(one("upcoming bills next week")).end)
    }

    @Test
    fun `bills as a spending category`() {
        assertEquals("bills", sum("how much did I spend on bills last month").category)
    }

    @Test
    fun `reminder`() {
        val q = assertIs<Query.CreateReminder>(one("remind me to pay rent tomorrow 7pm"))
        assertEquals("Pay rent", q.title)
        assertEquals(LocalDateTime.of(2026, 10, 5, 19, 0).atZone(IST).toInstant().toEpochMilli(), q.at)
        assertTrue(q.write)
    }

    @Test
    fun `event`() {
        val q = assertIs<Query.CreateEvent>(one("add dinner with Ravi to calendar saturday 8pm"))
        assertEquals(LocalDateTime.of(2026, 10, 10, 20, 0).atZone(IST).toInstant().toEpochMilli(), q.start)
        assertEquals(q.start + 3600_000L, q.end)
        assertTrue(q.title.startsWith("Dinner with Ravi"))
    }

    @Test
    fun `reminder without a time is not understood`() {
        assertEquals(emptyList(), p.plan("remind me to breathe", NOW).queries)
    }

    @Test
    fun `chatter is not explicit`() {
        assertFalse(p.plan("hello there", NOW).explicit)
        assertTrue(p.plan("swiggy", NOW).explicit)
        assertTrue(p.plan("how much did I spend", NOW).explicit)
    }
}
