package app.companion.core

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadsTest {
    private val z = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 5)
    private fun ms(d: LocalDate, h: Int = 12) = d.atTime(h, 0).atZone(z).toInstant().toEpochMilli()
    private fun led(id: Long, d: LocalDate, who: String, cat: String?, paise: Long, last4: String? = null, bank: String? = null) = Led(id, ms(d), who, cat, last4, bank, paise)
    private fun obj(s: String) = Json.obj(s)

    @Suppress("UNCHECKED_CAST")
    private fun rows(m: Map<String, Any?>) = m["rows"] as List<Map<String, Any?>>

    private val rows = listOf(
        led(1, today, "Swiggy", "food", 25_000, "1234", "HDFC Bank"),
        led(2, today.minusDays(1), "Swiggy", "food", 40_050, "9999", "ICICI Bank"),
        led(3, today.minusDays(2), "Uber", "transport", 18_000, "1234", "HDFC Bank"),
        led(4, today.minusDays(40), "Swiggy", "food", 99_000, "1234", "HDFC Bank"),
        led(5, today.minusDays(3), "Amazon", "shopping", 500_000),
    )
    private val month = today.withDayOfMonth(1)

    private fun f(merchant: String? = null, category: String? = null, card: String? = null, min: Long? = null, max: Long? = null, from: LocalDate = month, to: LocalDate = today) = Filt(merchant, category, card, min, max, from, to)

    @Test
    fun `rupees group the indian way`() {
        assertEquals("₹0", Rs.of(0))
        assertEquals("₹7,134", Rs.of(713_400))
        assertEquals("₹1,23,456.50", Rs.of(12_345_650))
        assertEquals("₹250", Rs.of(-25_000))
        assertEquals("-₹250", Rs.signed(-25_000))
        assertEquals("+₹250", Rs.signed(25_000))
    }

    @Test
    fun `ledger filters by period merchant category card and amount`() {
        assertEquals(listOf(1L, 2L, 3L, 5L), Ledgers.run(rows, f(), z).rows.map { it.id })
        assertEquals(listOf(1L, 2L), Ledgers.run(rows, f(merchant = "swig"), z).rows.map { it.id })
        assertEquals(listOf(1L, 2L), Ledgers.run(rows, f(category = "FOOD"), z).rows.map { it.id })
        assertEquals(listOf(1L, 3L), Ledgers.run(rows, f(card = "1234"), z).rows.map { it.id })
        assertEquals(listOf(1L, 3L), Ledgers.run(rows, f(card = "hdfc"), z).rows.map { it.id })
        assertEquals(listOf(1L, 3L), Ledgers.run(rows, f(card = "HDFC ··1234"), z).rows.map { it.id })
        assertEquals(listOf(2L, 5L), Ledgers.run(rows, f(min = 30_000), z).rows.map { it.id })
        assertEquals(listOf(1L, 3L), Ledgers.run(rows, f(max = 25_000), z).rows.map { it.id })
        assertEquals(listOf(4L), Ledgers.run(rows, f(from = today.minusDays(60), to = today.minusDays(30)), z).rows.map { it.id })
    }

    @Test
    fun `ledger caps rows but counts and totals everything`() {
        val many = (1L..20L).map { led(it, today, "Shop", "shopping", 10_000) }
        val p = Ledgers.run(many, f(), z)
        assertEquals(Ledgers.CAP, p.rows.size)
        assertEquals(20, p.n)
        assertEquals(200_000L, p.total)
        val empty = Ledgers.run(emptyList(), f(), z)
        assertEquals(0, empty.n)
        assertEquals(0L, empty.total)
    }

    @Test
    fun `ledger lists the newest first`() {
        assertEquals(listOf(1L, 2L, 3L, 5L, 4L), Ledgers.run(rows, f(from = today.minusDays(60)), z).rows.map { it.id })
    }

    @Test
    fun `top merchants group case insensitively and rank by spend`() {
        val r = rows + led(6, today, "SWIGGY", "food", 1_000)
        val t = Ledgers.top(r, 2, month, today, z)
        assertEquals(listOf("Amazon", "Swiggy"), t.rows.map { it.who })
        assertEquals(listOf(500_000L, 66_050L), t.rows.map { it.paise })
        assertEquals(listOf(1, 3), t.rows.map { it.n })
        assertEquals(3, t.n)
        assertEquals(500_000L + 66_050 + 18_000, t.total)
        assertEquals(1, Ledgers.top(r, 0, month, today, z).rows.size)
        assertEquals(Ledgers.CAP, Ledgers.top((1L..20L).map { led(it, today, "m$it", null, 100) }, 50, month, today, z).rows.size)
    }

    @Test
    fun `compare reports the difference and percent`() {
        val c = Cmp(150_000, 3, 100_000, 2)
        assertEquals(50_000L, c.diff)
        assertEquals(50, c.pct)
        assertEquals(-50, Cmp(50_000, 1, 100_000, 2).pct)
        assertNull(Cmp(50_000, 1, 0, 0).pct)
    }

    @Test
    fun `snippets stay within eighty characters and keep the match`() {
        assertEquals("short text", Snip.of("  short \n text ", "x"))
        val long = "Your order has shipped. " + "filler ".repeat(30) + "Refund of Rs 500 initiated for order 42 " + "tail ".repeat(30)
        val s = Snip.of(long, "refund")
        assertTrue(s.length <= 80, "${s.length}")
        assertTrue("Refund" in s)
        assertTrue(s.startsWith("…"))
        assertTrue(s.endsWith("…"))
        val head = Snip.of("a".repeat(200), "zzz")
        assertEquals(80, head.length)
        assertTrue(head.endsWith("…") && !head.startsWith("…"))
        assertEquals("one two", Snip.of("one\n\n  two", "one"))
        val tail = Snip.of("x".repeat(150) + " end match", "match")
        assertTrue(tail.length <= 80 && tail.endsWith("match") && tail.startsWith("…"))
        val emoji = Snip.of("😀".repeat(80), "q")
        assertTrue(emoji.length <= 80)
        assertTrue(emoji.removeSuffix("…").let { it.isEmpty() || !Character.isHighSurrogate(it.last()) })
    }

    private fun due(id: Long, title: String, name: String?, last4: String?, d: LocalDate?, paid: Boolean = false, paise: Long = 713_400) = DueRow(id, title, name, last4, d, paise, 36_000, paid)

    @Test
    fun `bills match by name or last four and order open first`() {
        val l = listOf(
            due(1, "HDFC card bill", "HDFC", "4321", today.plusDays(5)),
            due(2, "ICICI card bill", "ICICI", "7777", today.minusDays(2)),
            due(3, "Airtel bill", "Airtel", null, null),
            due(4, "ICICI card bill", "ICICI", "7777", today.minusDays(30), paid = true),
        )
        assertEquals(listOf(1L), Dues.pick(l, "hdfc").map { it.id })
        assertEquals(listOf(1L), Dues.pick(l, "card ··4321").map { it.id })
        assertEquals(listOf(2L, 4L), Dues.pick(l, "ICICI card").map { it.id })
        assertEquals(l, Dues.pick(l, null))
        assertEquals(l, Dues.pick(l, " "))
        assertEquals(emptyList(), Dues.pick(l, "sbi"))
        assertEquals(listOf(2L, 1L, 3L, 4L), Dues.order(l).map { it.id })
        assertEquals("overdue", Dues.status(l[1], today))
        assertEquals("due", Dues.status(l[0], today))
        assertEquals("due", Dues.status(l[2], today))
        assertEquals("paid", Dues.status(l[3], today))
        assertEquals("due", Dues.status(due(5, "x", null, null, today), today))
    }

    @Test
    fun `statement window gives the days a purchase today stays interest free`() {
        val w = Cycle.window(5, 25, today)
        assertEquals(LocalDate.of(2026, 11, 5), w.from)
        assertEquals(LocalDate.of(2026, 11, 25), w.to)
        val b = Cycle.window(20, 8, today)
        assertEquals(LocalDate.of(2026, 10, 20), b.from)
        assertEquals(LocalDate.of(2026, 11, 8), b.to)
        assertEquals(LocalDate.of(2026, 11, 4), Cycle.window(4, 25, today).from)
        assertEquals(LocalDate.of(2026, 11, 5), Cycle.window(5, 25, today).from)
        assertEquals(LocalDate.of(2026, 10, 6), Cycle.window(6, 25, today).from)
    }

    @Test
    fun `best card has the longest window`() {
        val r = Best.rank(listOf(Plastic("Axis ··1111", 20, 8), Plastic("HDFC ··2222", 5, 25), Plastic("SBI ··3333", 1, 20)), today)
        assertEquals(listOf("HDFC ··2222", "SBI ··3333", "Axis ··1111"), r.map { it.name })
        assertEquals(51, r[0].days)
        assertEquals(LocalDate.of(2026, 11, 25), r[0].due)
        assertTrue(r.zipWithNext().all { (a, b) -> a.days >= b.days })
        assertEquals(emptyList(), Best.rank(emptyList(), today))
    }

    @Test
    fun `weight view windows the points and averages by week`() {
        val pts = listOf(
            LocalDate.of(2026, 8, 1) to 80.0,
            LocalDate.of(2026, 9, 21) to 74.0,
            LocalDate.of(2026, 9, 24) to 73.0,
            LocalDate.of(2026, 10, 1) to 72.4,
            LocalDate.of(2026, 10, 5) to 72.0,
            LocalDate.of(2026, 10, 9) to 10.0,
        )
        val v = Weigh.view(pts, 4, today)!!
        assertEquals(4, v.n)
        assertEquals(74.0, v.first)
        assertEquals(72.0, v.last)
        assertEquals(listOf("2026-09-21", "2026-09-28", "2026-10-05"), v.weeks.map { it.from.toString() })
        assertEquals(listOf(73.5, 72.4, 72.0), v.weeks.map { it.kg })
        assertTrue(v.perWeek!! < 0)
        assertNull(Weigh.view(pts, 1, LocalDate.of(2026, 12, 1)))
        assertNull(Weigh.view(emptyList(), 8, today))
        assertNull(Weigh.view(listOf(today to 70.0), 8, today)!!.perWeek)
    }

    @Test
    fun `message result keeps rows short and ids reusable`() {
        val long = "x".repeat(200)
        val r = (1L..8L).map { MsgRow(it, "Sender name that is rather long", ms(today), Snip.of(long, "x")) }
        val s = Shape.msgs(30, r, z)
        val m = obj(s)
        assertEquals(30L, m["n"])
        assertEquals(Shape.MSGS, rows(m).size)
        assertEquals("i:1", rows(m)[0]["id"])
        assertEquals("2026-10-05", rows(m)[0]["d"])
        assertTrue((rows(m)[0]["from"] as String).length <= 20)
        assertTrue((rows(m)[0]["t"] as String).length <= 80)
        assertTrue(s.length <= 1100, "${s.length}")
    }

    @Test
    fun `ledger and top results carry formatted amounts`() {
        val p = Ledgers.run(rows, f(), z)
        val m = obj(Shape.ledger(p, z))
        assertEquals(4L, m["n"])
        assertEquals(Rs.of(25_000 + 40_050 + 18_000 + 500_000), m["amt"])
        assertEquals(mapOf("id" to "i:1", "d" to "2026-10-05", "who" to "Swiggy", "cat" to "food", "amt" to "₹250"), rows(m)[0])
        val t = obj(Shape.top(Ledgers.top(rows, 3, month, today, z)))
        assertEquals("Amazon", rows(t)[0]["who"])
        assertEquals(1L, rows(t)[0]["n"])
        assertTrue(Shape.ledger(Ledgers.run((1L..20L).map { led(it, today, "A long merchant name here", "entertainment", 12_345_600) }, f(), z), z).length <= 1100)
    }

    @Test
    fun `compare result shows both sides the signed change and percent`() {
        val m = obj(Shape.compare("this month", "last month", Cmp(150_000, 3, 100_000, 2)))
        assertEquals(mapOf("p" to "this month", "amt" to "₹1,500", "n" to 3L), m["a"])
        assertEquals(mapOf("p" to "last month", "amt" to "₹1,000", "n" to 2L), m["b"])
        assertEquals("+₹500", m["diff"])
        assertEquals(50L, m["pct"])
        assertTrue(!obj(Shape.compare("a", "b", Cmp(1, 1, 0, 0))).containsKey("pct"))
    }

    @Test
    fun `needs result lists kind state and optional amount`() {
        val s = Shape.needs(12, listOf(NeedRow(5, "Debit", "Zomato", 25_000, ms(today), "ask"), NeedRow(6, "Personal", "Riya", 0, ms(today), "check")), z)
        val m = obj(s)
        assertEquals(12L, m["n"])
        assertEquals(mapOf("id" to "i:5", "k" to "Debit", "t" to "Zomato", "amt" to "₹250", "d" to "2026-10-05", "s" to "ask"), rows(m)[0])
        assertTrue(!rows(m)[1].containsKey("amt"))
    }

    @Test
    fun `bill result carries due minimum and status`() {
        val m = obj(Shape.dues(listOf(due(9, "ICICI card bill", "ICICI", "7777", today.plusDays(7)), due(3, "Airtel bill", null, null, null, paid = true)), today))
        assertEquals(mapOf("id" to "i:9", "t" to "ICICI card bill", "due" to "2026-10-12", "amt" to "₹7,134", "min" to "₹360", "st" to "due"), rows(m)[0])
        assertEquals("paid", rows(m)[1]["st"])
        assertTrue(!rows(m)[1].containsKey("due"))
    }

    @Test
    fun `best card result names the winner`() {
        val m = obj(Shape.best(Best.rank(listOf(Plastic("HDFC ··2222", 5, 25), Plastic("Axis ··1111", 20, 8)), today)))
        assertEquals("HDFC ··2222", m["best"])
        assertEquals(mapOf("card" to "HDFC ··2222", "days" to 51L, "stmt" to "2026-11-05", "due" to "2026-11-25"), rows(m)[0])
        assertTrue(!obj(Shape.best(emptyList())).containsKey("best"))
    }

    @Test
    fun `meals result totals the day`() {
        val m = obj(Shape.meals(today, 1840, 2200, listOf(MealSum("lunch", 620, listOf("roti", "dal")), MealSum("dinner", 900, emptyList()))))
        assertEquals("2026-10-05", m["d"])
        assertEquals(1840L, m["kcal"])
        assertEquals(2200L, m["goal"])
        assertEquals(mapOf("slot" to "lunch", "kcal" to 620L, "items" to "roti, dal"), rows(m)[0])
        assertTrue(!obj(Shape.meals(today, 0, null, emptyList())).containsKey("goal"))
    }

    @Test
    fun `weight result rounds to one decimal`() {
        val v = Weigh.view(listOf(LocalDate.of(2026, 9, 28) to 73.0, LocalDate.of(2026, 10, 2) to 72.0, today to 71.6), 4, today)!!
        val m = obj(Shape.weight(v))
        assertEquals(71.6, m["last"])
        assertEquals(73.0, m["first"])
        assertEquals(2, rows(m).size)
        assertEquals(72.5, rows(m)[0]["kg"])
        assertEquals(71.6, rows(m)[1]["kg"])
        assertTrue(m["wk"] is Double)
    }

    @Test
    fun `meeting result shows local times and the source`() {
        val a = Meeting(1, "Standup", ms(today, 10), ms(today, 11), null, Join(Via.Meet, "https://meet.google.com/abc-defg-hij"), 1)
        val b = Meeting(2, "", ms(today, 15), ms(today, 16), "Office, 4th floor", null, 1)
        val m = obj(Shape.meets(today, listOf(a, b), z))
        assertEquals(2L, m["n"])
        assertEquals(mapOf("t" to "Standup", "at" to "10:00", "to" to "11:00", "via" to "Meet"), rows(m)[0])
        assertEquals("Meeting", rows(m)[1]["t"])
        assertEquals("Office, 4th floor", rows(m)[1]["via"])
        assertEquals(0L, obj(Shape.meets(today, emptyList(), z))["n"])
    }

    @Test
    fun `trip result lists spend and flags the active one`() {
        val m = obj(Shape.trips(listOf(TripSum(3, "Goa", today.minusDays(6), today, true, 4_500_000, 7), TripSum(2, "Jaipur", today.minusDays(90), today.minusDays(85), false, 1_000_000, 6))))
        assertEquals(mapOf("id" to "t:3", "t" to "Goa", "from" to "2026-09-29", "to" to "2026-10-05", "on" to true, "amt" to "₹45,000", "days" to 7L), rows(m)[0])
        assertTrue(!rows(m)[1].containsKey("on"))
    }
}
