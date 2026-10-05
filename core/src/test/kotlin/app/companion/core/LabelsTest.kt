package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class LabelsTest {
    private val debit = Event.Debit(11_100, "INR", "1234", "ACME", "ACME STORE", Mode.Upi)
    private val credit = Event.Credit(11_100, "INR", "1234", "ACME", "ACME STORE", Mode.Other)
    private val card = Event.CardSpend(11_100, "INR", "1234", "ACME", "ACME STORE")
    private val bill = Event.Bill(11_100, "INR", day(2026, 10, 20), null, "ACME", "1234")
    private val stmt = Event.Statement(11_100, 500, day(2026, 10, 20), "1234", "ACME")
    private val otp = Event.Otp("123456", NOW, "ACME", null)

    @Test
    fun `every label maps to its kind`() {
        val want = mapOf(
            "otp" to Kind.Otp, "expense" to Kind.Debit, "income" to Kind.Credit, "bill" to Kind.Bill, "delivery" to Kind.Delivery,
            "alert" to Kind.Alert, "personal" to Kind.Personal, "promo" to Kind.Promo, "spam" to Kind.Spam,
        )
        want.forEach { (l, k) -> assertEquals(k, Labels.kind(l, Event.Unknown), l) }
        assertNull(Labels.kind("other", Event.Unknown))
    }

    @Test
    fun `expense is a card spend only when the rules found a card`() {
        assertEquals(Kind.CardSpend, Labels.kind("expense", card))
        assertEquals(Kind.Debit, Labels.kind("expense", debit))
        assertEquals(Kind.Debit, Labels.kind("expense", credit))
        assertEquals(Kind.Debit, Labels.kind("expense", Event.Unknown))
    }

    @Test
    fun `bill is a statement only when the rules found a statement`() {
        assertEquals(Kind.Statement, Labels.kind("bill", stmt))
        assertEquals(Kind.Bill, Labels.kind("bill", bill))
        assertEquals(Kind.Bill, Labels.kind("bill", debit))
    }

    @Test
    fun `rule fields win when the label changes a money event`() {
        assertEquals(debit, Labels.event("expense", debit))
        assertEquals(card, Labels.event("expense", card))
        assertEquals(Event.Credit(11_100, "INR", "1234", "ACME", "ACME STORE", Mode.Upi), Labels.event("income", debit))
        assertEquals(Event.Debit(11_100, "INR", "1234", "ACME", "ACME STORE", Mode.Other), Labels.event("expense", credit))
        assertEquals(Event.Credit(11_100, "INR", "1234", "ACME", "ACME STORE", Mode.Card), Labels.event("income", card))
    }

    @Test
    fun `bill keeps the rule bill and statement and builds one from an amount`() {
        assertEquals(bill, Labels.event("bill", bill))
        assertEquals(stmt, Labels.event("bill", stmt))
        val b = assertIs<Event.Bill>(Labels.event("bill", debit))
        assertEquals(11_100L, b.paise)
        assertEquals("1234", b.last4)
        assertEquals("ACME STORE", b.biller)
        assertNull(b.due)
    }

    @Test
    fun `a label needing fields the rules did not find gives no event`() {
        assertNull(Labels.event("expense", Event.Unknown))
        assertNull(Labels.event("income", Event.Alert))
        assertNull(Labels.event("bill", Event.Alert))
        assertNull(Labels.event("delivery", Event.Alert))
        assertNull(Labels.event("otp", Event.Alert))
        assertNull(Labels.event("expense", bill))
    }

    @Test
    fun `delivery and otp pass through the rule event`() {
        val d = Event.Delivery("ACME", Stage.Out)
        assertEquals(d, Labels.event("delivery", d))
        assertEquals(otp, Labels.event("otp", otp))
    }

    @Test
    fun `a sure delivery the rules missed still files as a delivery`() {
        val raw = { body: String -> Raw(Source.Sms, "AD-ACMESH-S", "", body, 0) }
        assertEquals(Event.Delivery(null, Stage.Update), Labels.event("delivery", Event.Unknown, raw("Your package status changed, see link")))
        assertEquals(Event.Delivery(null, Stage.Shipped), Labels.event("delivery", Event.Alert, raw("Item has been dispatched")))
        assertNull(Labels.event("delivery", Event.Alert))
    }

    @Test
    fun `a sure alert overrides a non money guess but never a code or money`() {
        assertEquals(Event.Alert, Labels.event("alert", Event.Promo))
        assertEquals(Event.Alert, Labels.event("alert", Event.Delivery("ACME", Stage.Out)))
        assertNull(Labels.event("alert", otp))
        assertNull(Labels.event("alert", bill))
    }

    @Test
    fun `alert personal promo and spam`() {
        assertEquals(Event.Alert, Labels.event("alert", Event.Unknown))
        assertEquals(Event.Alert, Labels.event("alert", Event.Alert))
        assertNull(Labels.event("alert", debit))
        assertEquals(Event.Personal, Labels.event("personal", Event.Unknown))
        assertNull(Labels.event("personal", debit))
        assertEquals(Event.Promo, Labels.event("promo", Event.Unknown))
        assertEquals(Event.Spam, Labels.event("spam", Event.Alert))
        assertNull(Labels.event("promo", otp))
        assertNull(Labels.event("spam", otp))
    }

    @Test
    fun `category labels map to the enum`() {
        val want = mapOf(
            "food" to Category.Food, "groceries" to Category.Groceries, "shopping" to Category.Shopping, "transport" to Category.Transport,
            "travel" to Category.Travel, "bills" to Category.Bills, "entertainment" to Category.Entertainment, "health" to Category.Health,
            "transfer" to Category.Transfer, "other" to Category.Other,
        )
        want.forEach { (l, c) -> assertEquals(c, Labels.category(l), l) }
        assertNull(Labels.category("income"))
        assertNull(Labels.category("nope"))
    }

    @Test
    fun `spam has a type label and every kind still has one`() {
        assertEquals("spam", Types.of(Kind.Spam))
        assertEquals(Kind.entries.size, Kind.entries.map { it.name }.toSet().size)
    }
}
