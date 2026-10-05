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

    private fun explicit(text: String) = p.plan(text, NOW).explicit

    @Test
    fun `meal statements are never rule answers`() {
        listOf(
            "lunch was 2 rotis, dal and a bowl of curd",
            "I had 2 rotis and dal",
            "ate a banana for breakfast",
            "subah do paratha khaya",
            "chai piya",
            "dinner was biryani",
            "what did I eat today",
        ).forEach { assertFalse(explicit(it), it) }
        assertEquals(emptyList(), p.plan("lunch was 2 rotis, dal and a bowl of curd", NOW).queries)
    }

    @Test
    fun `money questions about meals stay rule answers`() {
        assertTrue(explicit("how much did I spend on lunch"))
        assertTrue(explicit("dinner kharcha this week"))
        assertEquals("food", sum("how much did I spend on dinner this month").category)
    }

    @Test
    fun `clear money phrasings are explicit`() {
        listOf(
            "how much did I spend on food this month", "food this month", "food spend this month", "Swiggy this week",
            "spent today", "spent this week", "bills due this week", "kitna kharcha hua", "total on card 4417", "rs 500 swiggy",
            "top merchants", "food yesterday",
        ).forEach { assertTrue(explicit(it), it) }
    }

    @Test
    fun `this month for food runs from the first to today`() {
        listOf("how much did I spend on food this month", "food spend this month", "food this month").forEach {
            val q = sum(it)
            assertEquals("food", q.category, it)
            assertEquals(null, q.merchant, it)
            assertEquals(day(2026, 10, 1), q.start, it)
            assertEquals(today, q.end, it)
        }
    }

    @Test
    fun `unresolved words defer to the model`() {
        assertFalse(explicit("how much did I spend at zq91 this month"))
        assertFalse(explicit("2 rotis dal curd this month"))
        assertFalse(explicit("hello this month"))
        assertTrue(explicit("how much at tokai this month"))
    }

    @Test
    fun `a bare category or loose word is not a money question`() {
        assertFalse(explicit("food"))
        assertFalse(explicit("coffee"))
    }

    @Test
    fun `agent phrasings go to the chat model`() {
        listOf(
            "Mark my HDFC bill paid", "mark ICICI card bill as paid", "What needs me today?", "Swiggy vs last month", "compare food this month with last month",
            "rename Swiggy Ltd to Swiggy", "file the Zomato payment under food", "set budget to 50000", "stop counting Zerodha as spending", "dismiss that",
        ).forEach { assertFalse(p.plan(it, NOW).explicit, it) }
        assertTrue(p.plan("Swiggy this week", NOW).explicit)
        assertTrue(p.plan("bills due this week", NOW).explicit)
        assertTrue(p.plan("how much did I spend on food last month", NOW).explicit)
    }
}
