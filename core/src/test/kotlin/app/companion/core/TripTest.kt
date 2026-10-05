package app.companion.core

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripTest {
    private val z = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 5)
    private fun ms(d: LocalDate, h: Int = 0) = d.atTime(h, 0).atZone(z).toInstant().toEpochMilli()
    private fun d(n: Long) = today.plusDays(n)
    private val now = ms(today, 10)

    @Test
    fun `tag picks the trip containing the instant`() {
        val spans = listOf(1L to TSpan(100, 200), 2L to TSpan(300, 400))
        assertEquals(1L, Trips.tag(100, spans))
        assertEquals(1L, Trips.tag(200, spans))
        assertEquals(2L, Trips.tag(350, spans))
        assertNull(Trips.tag(250, spans))
        assertNull(Trips.tag(99, spans))
        assertNull(Trips.tag(5, emptyList()))
    }

    @Test
    fun `tag prefers the narrowest window then the latest start`() {
        assertEquals(2L, Trips.tag(150, listOf(1L to TSpan(0, 1000), 2L to TSpan(100, 200))))
        assertEquals(3L, Trips.tag(150, listOf(1L to TSpan(100, 200), 3L to TSpan(120, 220), 2L to TSpan(50, 150))))
    }

    private fun conv(s: String) = assertNotNull(Forex.parse(s), s)

    @Test
    fun `inr per unit forms`() {
        conv("1 USD = INR 83.2").let { assertEquals(Forex.Conv(83.2, "USD", "INR"), it) }
        conv("Conversion rate: 1 EUR = 90.5 INR").let { assertEquals(Forex.Conv(90.5, "EUR", "INR"), it) }
        conv("1 GBP = Rs. 105.25").let { assertEquals(Forex.Conv(105.25, "GBP", "INR"), it) }
        conv("1 USD = ₹83.2").let { assertEquals(Forex.Conv(83.2, "USD", "INR"), it) }
        conv("1,000 JPY = 560 INR").let { assertEquals(0.56, it.rate, 1e-9) }
    }

    @Test
    fun `inverse forms divide`() {
        val c = conv("INR 1 = USD 0.012")
        assertEquals("USD", c.from)
        assertEquals("INR", c.to)
        assertEquals(1 / 0.012, c.rate, 1e-9)
        assertEquals(1 / 0.011, conv("1 INR = 0.011 EUR").rate, 1e-9)
    }

    @Test
    fun `at sign and keyword forms`() {
        assertEquals(Forex.Conv(83.10, "USD", "INR"), conv("Spent (USD 12.50 @ 83.10) at Cafe"))
        assertEquals(Forex.Conv(83.10, "USD", "INR"), conv("12.50 USD @ INR 83.10"))
        assertEquals(Forex.Conv(83.15, "", "INR"), conv("Txn converted at 83.15 on 04-10-26"))
        assertEquals(Forex.Conv(83.15, "", "INR"), conv("Forex rate of Rs. 83.15."))
        assertEquals(Forex.Conv(83.15, "", "INR"), conv("Exchange rate: 83.15 INR"))
    }

    @Test
    fun `rejects nonsense`() {
        listOf(
            "no rate here", "", "1 USD = 0.92 EUR", "converted at 0", "1 USD = INR 0", "1 USD = INR 9999999",
            "1 INR = 99999 USD", "Rs 500 spent at Cafe", "Conversion rate 1 EUR = 0.011 USD", "converted at",
        ).forEach { assertNull(Forex.parse(it), it) }
    }

    @Test
    fun `foreign amounts convert to paise`() {
        assertEquals(103875, Forex.inr(1250, Forex.Conv(83.10, "USD", "INR")))
        assertEquals(1, Forex.inr(1, Forex.Conv(0.6, "X", "INR")))
        assertEquals(1000, Forex.inr(12, Forex.Conv(0.012, "INR", "USD")))
        assertEquals(0, Forex.inr(0, Forex.Conv(83.1, "USD", "INR")))
    }

    private fun item(id: Long, p: Long, c: String?, share: Double = 1.0, day: LocalDate = today) = TItem(id, ms(day, 12), p, c, null, share)

    @Test
    fun `summary totals per day and top categories`() {
        val items = listOf(
            item(1, 100000, "food"), item(2, 50000, "food"), item(3, 300000, "travel"), item(4, 10000, null),
            item(5, 20000, "shopping"), item(6, 5000, "a"), item(7, 4000, "b"), item(8, 3000, "c"),
        )
        val s = TripSummary.of(items, ms(today), ms(d(3), 23), z)
        assertEquals(492000, s.total)
        assertEquals(4, s.days)
        assertEquals(123000, s.perDay)
        assertEquals(listOf("travel" to 300000L, "food" to 150000L, "shopping" to 20000L, "other" to 10000L, "a" to 5000L), s.top)
        assertTrue(s.split.isEmpty())
    }

    @Test
    fun `shares scale the total and categories`() {
        val s = TripSummary.of(listOf(item(1, 100000, "food", 0.5), item(2, 30001, "food", 0.5)), ms(today), ms(today), z)
        assertEquals(65001, s.total)
        assertEquals(1, s.days)
        assertEquals(65001, s.perDay)
        assertEquals(listOf("food" to 65001L), s.top)
    }

    @Test
    fun `days are inclusive calendar days and never below one`() {
        assertEquals(1, TripSummary.of(emptyList(), ms(today, 1), ms(today, 23), z).days)
        assertEquals(1, TripSummary.of(emptyList(), ms(d(2)), ms(today), z).days)
        assertEquals(2, TripSummary.of(emptyList(), ms(today, 23), ms(d(1), 1), z).days)
        assertEquals(0, TripSummary.of(emptyList(), ms(today), ms(today), z).total)
    }

    @Test
    fun `split is an equal tally among named people`() {
        val items = listOf(item(1, 30000, "food"), item(2, 10001, "food"), item(3, 5000, "food"), item(4, 7000, "food"))
        val who = mapOf(1L to listOf("Asha", "Ravi", "Me"), 2L to listOf("Asha", "Ravi"), 3L to listOf(" Me ", "Me", ""), 4L to emptyList())
        val s = TripSummary.of(items, ms(today), ms(today), z, who)
        assertEquals(mapOf("Asha" to 10000L + 5001, "Ravi" to 10000L + 5000, "Me" to 10000L + 5000), s.split)
        assertEquals(30000L + 10001 + 5000, s.split.values.sum())
    }

    private fun hint(w: String, d: LocalDate?, t: String = "") = TravelHint(w, d, t)

    @Test
    fun `outbound plus return is a trip`() {
        val p = TripSummary.suggest(listOf(hint("Flight", d(10), "Flight to Goa on 15 Oct"), hint("Flight", d(14), "Flight to Bengaluru")), now, z)
        assertEquals(1, p.size)
        assertEquals("Trip to Goa", p[0].name)
        assertEquals(ms(d(10)), p[0].start)
        assertEquals(ms(d(15)) - 1, p[0].end)
    }

    @Test
    fun `outbound plus stay is a trip and a lone stay too`() {
        val a = TripSummary.suggest(listOf(hint("Train", d(3)), hint("Stay", d(3), "Hotel booking in New Delhi")), now, z)
        assertEquals(listOf("Trip to New Delhi"), a.map { it.name })
        val b = TripSummary.suggest(listOf(hint("Stay", d(4), "Booking confirmed")), now, z)
        assertEquals(listOf("Trip Oct 2026"), b.map { it.name })
        assertEquals(b[0].start, ms(d(4)))
        assertEquals(b[0].end, ms(d(5)) - 1)
    }

    @Test
    fun `a lone outbound or same day outbounds are not a trip`() {
        assertTrue(TripSummary.suggest(listOf(hint("Flight", d(5))), now, z).isEmpty())
        assertTrue(TripSummary.suggest(listOf(hint("Flight", d(5)), hint("Train", d(5))), now, z).isEmpty())
        assertTrue(TripSummary.suggest(listOf(hint("Flight", d(5)), hint("Flight", d(40))), now, z).isEmpty())
    }

    @Test
    fun `past undated and unrelated hints are ignored`() {
        assertTrue(TripSummary.suggest(listOf(hint("Stay", d(-3)), hint("Flight", null), hint("Bus", d(2)), hint("Stay", null)), now, z).isEmpty())
        assertTrue(TripSummary.suggest(emptyList(), now, z).isEmpty())
    }

    @Test
    fun `separate windows give separate trips in order`() {
        val p = TripSummary.suggest(
            listOf(hint("Stay", d(60), "Hotel in Jaipur"), hint("Train", d(2), "Train to Pune"), hint("Train", d(5)), hint("Flight", d(62))),
            now, z,
        )
        assertEquals(listOf("Trip to Pune", "Trip to Jaipur"), p.map { it.name })
        assertEquals(ms(d(2)), p[0].start)
        assertEquals(ms(d(6)) - 1, p[0].end)
        assertEquals(ms(d(63)) - 1, p[1].end)
    }

    @Test
    fun `destination words skip filler`() {
        val p = TripSummary.suggest(listOf(hint("Stay", d(2), "Your booking in Progress"), hint("Flight", d(3), "invoice for Flight")), now, z)
        assertEquals("Trip Oct 2026", p[0].name)
        val q = TripSummary.suggest(listOf(hint("Stay", d(2), "Hotel in Your Goa")), now, z)
        assertEquals("Trip Oct 2026", q[0].name)
    }
}
