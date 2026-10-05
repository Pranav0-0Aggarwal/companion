package app.companion.core

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlertsTest {
    private val utc = ZoneId.of("UTC")
    private val day = LocalDate.of(2026, 10, 5)
    private fun at(d: LocalDate, h: Int) = d.atTime(h, 0).atZone(utc).toInstant().toEpochMilli()

    @Test
    fun `only money bills and deliveries get a channel`() {
        assertEquals(Chan.Spend, Alerts.chan(Kind.Debit))
        assertEquals(Chan.Spend, Alerts.chan(Kind.CardSpend))
        assertEquals(Chan.Spend, Alerts.chan(Kind.Credit))
        assertEquals(Chan.Bill, Alerts.chan(Kind.Bill))
        assertEquals(Chan.Bill, Alerts.chan(Kind.Statement))
        assertEquals(Chan.Delivery, Alerts.chan(Kind.Delivery))
        listOf(Kind.Otp, Kind.Promo, Kind.Spam, Kind.Alert, Kind.Personal, Kind.Unknown, Kind.Travel).forEach { assertNull(Alerts.chan(it), it.name) }
    }

    @Test
    fun `a switched off channel stays quiet`() {
        val o = Opts(spend = false, delivery = false)
        assertFalse(Alerts.wants(o, Kind.Debit))
        assertFalse(Alerts.wants(o, Kind.Delivery))
        assertTrue(Alerts.wants(o, Kind.Bill))
        assertFalse(Alerts.wants(Opts(), Kind.Spam))
    }

    @Test
    fun `old messages never notify`() {
        val now = 100 * Alerts.FRESH
        assertTrue(Alerts.fresh(now - 1000, now))
        assertTrue(Alerts.fresh(now - Alerts.FRESH, now))
        assertFalse(Alerts.fresh(now - Alerts.FRESH - 1, now))
        assertFalse(Alerts.fresh(now + 3_600_000L, now))
    }

    @Test
    fun `each channel owns a distinct bit clear of the reminder and suggestion bits`() {
        val bits = Chan.entries.map(Alerts::bit)
        assertEquals(3, bits.toSet().size)
        bits.forEach { assertEquals(0, it and (1 or 2 or 4 or 8)) }
        assertEquals(bits.sum(), Alerts.seen)
    }

    @Test
    fun `the lock screen shows the redacted face unless details are on`() {
        val full = Alerts.spend("₹261", "Swiggy", "food", "HDFC", "4021", false)
        assertEquals(Hidden.spend, Alerts.shown(Opts(), full, Hidden.spend))
        assertEquals(full, Alerts.shown(Opts(lock = true), full, Hidden.spend))
        listOf(Hidden.spend, Hidden.bill, Hidden.due, Hidden.order, Hidden.daily).forEach { f ->
            assertFalse(Regex("[₹0-9]").containsMatchIn(f.title + f.text.orEmpty()))
        }
    }

    @Test
    fun `a spend reads amount then merchant over category and card`() {
        val f = Alerts.spend("₹261", "Swiggy", "food", "HDFC", "4021", false)
        assertEquals("₹261 · Swiggy", f.title)
        assertEquals("Food · HDFC ··4021", f.text)
        assertEquals("+₹5,000 · Acme", Alerts.spend("₹5,000", "Acme", null, null, null, true).title)
        assertNull(Alerts.spend("₹5", "Acme", null, null, null, false).text)
        assertEquals("Today · ₹1,240 across 4", Alerts.total("₹1,240", 4).title)
    }

    @Test
    fun `a bill reads biller amount and due date`() {
        assertEquals("ICICI card bill ₹9,892.75 · due 20 Oct", Alerts.bill("ICICI card bill", "₹9,892.75", "20 Oct").title)
        assertEquals("Rent", Alerts.bill("Rent", null, null).title)
        assertEquals("Rent · due 1 Nov", Alerts.bill("Rent", null, "1 Nov").title)
    }

    @Test
    fun `a bill reminder lands on the morning before it is due`() {
        val due = LocalDate.of(2026, 10, 20)
        assertEquals(at(due.minusDays(1), 9), Alerts.remindAt(due, at(day, 12), utc))
        assertEquals(at(due, 9), Alerts.remindAt(due, at(due.minusDays(1), 10), utc))
        val late = at(due, 11)
        assertEquals(late + 3_600_000L, Alerts.remindAt(due, late, utc))
    }

    @Test
    fun `the digest fires at 8 pm today or tomorrow`() {
        assertEquals(2 * 3_600_000L, Alerts.untilDigest(at(day, 18), utc))
        assertEquals(23 * 3_600_000L, Alerts.untilDigest(at(day, 21), utc))
        assertEquals(24 * 3_600_000L, Alerts.untilDigest(at(day, 20), utc))
        assertEquals(at(day, 0), Alerts.dayStart(at(day, 15), utc))
    }

    @Test
    fun `the digest names only what is there`() {
        assertEquals("3 messages need you · 1 bill due tomorrow · ₹1,240 spent today", Digest.text(3, 0, 1, "₹1,240"))
        assertEquals("1 message needs you", Digest.text(1, 0, 0, null))
        assertEquals("2 bills due today · 1 bill due tomorrow", Digest.text(0, 2, 1, null))
        assertEquals("₹90 spent today", Digest.text(0, 0, 0, "₹90"))
        assertNull(Digest.text(0, 0, 0, null))
    }

    @Test
    fun `delivery stages map to progress and update never moves it`() {
        assertEquals(listOf(0, 100, 200, 300), listOf(Stage.Placed, Stage.Shipped, Stage.Out, Stage.Delivered).map { Track.at(it) })
        assertNull(Track.at(Stage.Update))
        assertEquals(Track.MAX, Track.at(Stage.Delivered))
        assertTrue(Track.points.all { it in 1 until Track.MAX })
        assertEquals(Stage.Out, Track.latest(listOf(Stage.Placed, Stage.Out, Stage.Update)))
        assertEquals(Stage.Delivered, Track.latest(listOf(Stage.Delivered, Stage.Out)))
        assertNull(Track.latest(listOf(Stage.Update)))
    }

    @Test
    fun `the stage reads back from the item title`() {
        Stage.entries.forEach { s ->
            assertEquals(s, Track.stage("Swiggy ${Track.word(s)}"))
            assertEquals(s, Track.stage("Order ${Track.word(s)}"))
        }
        assertNull(Track.stage("Swiggy bill"))
    }

    @Test
    fun `an order is keyed by merchant and day`() {
        val a = Track.key("Swiggy", at(day, 12), utc)
        assertEquals(a, Track.key("swiggy ", at(day, 19), utc))
        assertTrue(a != Track.key("Swiggy", at(day.plusDays(1), 12), utc))
        assertTrue(a != Track.key("Zomato", at(day, 12), utc))
        assertTrue(Track.same("Swiggy Instamart", "Swiggy"))
        assertFalse(Track.same(null, "Swiggy"))
        assertFalse(Track.same("Amazon", "Swiggy"))
    }

    @Test
    fun `the cover leads with a delivery then a code then a bill then spend`() {
        assertEquals(listOf(Lane.Out, Lane.Code, Lane.Due, Lane.Spent), Cover.lanes(true, true, 3, 2))
        assertEquals(listOf(Lane.Code, Lane.Spent), Cover.lanes(false, true, 12, 1))
        assertEquals(listOf(Lane.Due), Cover.lanes(false, false, -2, 0))
        assertEquals(listOf(Lane.Due), Cover.lanes(false, false, 7, 0))
        assertEquals(emptyList(), Cover.lanes(false, false, null, 0))
    }

    @Test
    fun `the cover only trusts codes under ten minutes old and words dues plainly`() {
        assertTrue(Cover.code(1_000, 1_000 + Cover.CODE - 1))
        assertFalse(Cover.code(1_000, 1_000 + Cover.CODE))
        assertEquals(listOf("overdue 2d", "due today", "due tomorrow", "due in 5 days"), listOf(-2, 0, 1, 5).map(Cover::due))
        assertEquals("₹9", Cover.amount(Opts(), "₹9"))
        assertEquals(Cover.MASK, Cover.amount(Opts(hide = true), "₹9"))
    }
}
