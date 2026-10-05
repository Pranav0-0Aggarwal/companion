package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RulesTest {
    private val strict = RulesClassifier(IST, 0.995f)
    private val otp = strict.classify(sms("VM-ACMEBK", "123456 is your OTP. Valid for 5 minutes."))
    private val spend = strict.classify(sms("VM-ACMEBK", "Rs 111.00 spent on card XX1234 at ACME STORE on 04-10-26"))
    private val notice = RulesClassifier(IST).classify(sms("VM-ACMEBK-S", "Your plan renewal summary"))

    @Test
    fun `one correction teaches a rule and repeats keep counting`() {
        var count = 0
        var label: String? = null
        listOf("food", "food").forEach {
            count = Rules.bump(label, count, it)
            label = it
        }
        assertEquals(1, Rules.MIN)
        assertEquals(2, count)
        assertEquals(2, Rules.bump("food", 1, "food"))
        assertEquals(1, Rules.bump(null, 0, "food"))
    }

    @Test
    fun `merged rules keep the higher count or sum the same label`() {
        assertEquals("promo" to 5, Rules.merge("promo" to 2, "promo" to 3))
        assertEquals("spam" to 4, Rules.merge("promo" to 2, "spam" to 4))
        assertEquals("promo" to 4, Rules.merge("promo" to 4, "spam" to 2))
        assertEquals("promo" to 3, Rules.merge("promo" to 3, "spam" to 3))
    }

    @Test
    fun `a different answer restarts the count`() {
        assertEquals(1, Rules.bump("food", 5, "bills"))
    }

    @Test
    fun `no rules leave the verdict alone`() {
        val (v, c) = Rules.apply(otp, emptyMap())
        assertSame(otp, v)
        assertNull(c)
    }

    @Test
    fun `a learned alert turns a code into an alert and settles it`() {
        assertIs<Event.Otp>(otp.event)
        val (v, c) = Rules.apply(otp, mapOf("type" to "alert"))
        assertIs<Verdict.Sure>(v)
        assertEquals(Event.Alert, v.event)
        assertNull(c)
    }

    @Test
    fun `a learned promo or spam settles an unsure notice`() {
        assertEquals(Event.Promo, Rules.apply(notice, mapOf("type" to "promo")).first.event)
        assertEquals(Event.Spam, Rules.apply(notice, mapOf("type" to "spam")).first.event)
    }

    @Test
    fun `money is never retyped by a rule`() {
        assertIs<Event.Move>(spend.event)
        val (v, _) = Rules.apply(spend, mapOf("type" to "alert"))
        assertSame(spend, v)
    }

    @Test
    fun `a confirming type rule settles the same event`() {
        assertIs<Verdict.Unsure>(notice)
        val (v, _) = Rules.apply(notice, mapOf("type" to "alert"))
        assertIs<Verdict.Sure>(v)
        assertEquals(notice.event, v.event)
    }

    @Test
    fun `a type rule for another kind does nothing`() {
        assertSame(otp, Rules.apply(otp, mapOf("type" to "bill")).first)
    }

    @Test
    fun `a category rule files money and carries the label`() {
        assertIs<Verdict.Unsure>(spend)
        val (v, c) = Rules.apply(spend, mapOf("category" to "shopping"))
        assertIs<Verdict.Sure>(v)
        assertEquals(spend.event, v.event)
        assertEquals("shopping", c)
    }

    @Test
    fun `a category rule is ignored for other events`() {
        val (v, c) = Rules.apply(notice, mapOf("category" to "shopping"))
        assertSame(notice, v)
        assertNull(c)
    }

    @Test
    fun `a rule keeps the tags and category of the verdict`() {
        val u = Verdict.Unsure(Event.Unknown, 0.4f, Guess("promo", 0.93f), listOf("spam"), Guess("food", 0.99f))
        val (w, _) = Rules.apply(u, mapOf("type" to "promo"))
        assertEquals(listOf("spam"), w.tags)
        assertEquals(Guess("food", 0.99f), w.cat)
    }

    @Test
    fun `sure verdicts keep their confidence and guess`() {
        val s = Verdict.Sure(Event.Alert, 0.98f, Guess("alert", 0.98f))
        val (v, _) = Rules.apply(s, mapOf("type" to "alert"))
        assertSame(s, v)
        val u = Verdict.Unsure(Event.Unknown, 0.4f, Guess("promo", 0.93f))
        val (w, _) = Rules.apply(u, mapOf("type" to "promo"))
        assertEquals(Guess("promo", 0.93f), w.guess)
        assertTrue(w is Verdict.Sure)
    }

    private val debit = Event.Debit(10000, "INR", "1234", "Acme Bank", "ACME STORE", Mode.Upi)
    private val unknown = Verdict.Unsure(Event.Unknown, 0.4f)
    private val raw = sms("VM-ACMEBK", "Your order from ACME has shipped")

    private fun typed(v: Verdict, label: String, r: Raw? = null) = Rules.apply(v, mapOf("type" to label), r).first

    @Test
    fun `a delivery rule files an unsure notice as a delivery`() {
        val v = typed(unknown, "delivery", raw)
        assertIs<Verdict.Sure>(v)
        assertIs<Event.Delivery>(v.event)
    }

    @Test
    fun `a delivery rule without text does nothing`() {
        assertSame(unknown, typed(unknown, "delivery"))
    }

    @Test
    fun `a personal rule turns notices into personal but not money or bills`() {
        assertEquals(Event.Personal, typed(unknown, "personal").event)
        assertEquals(Event.Personal, typed(Verdict.Sure(Event.Promo, 1f), "personal").event)
        val m = Verdict.Sure(debit, 1f)
        assertSame(m, typed(m, "personal"))
        val b = Verdict.Sure(Event.Bill(100, "INR", null, null, "ACME", null), 1f)
        assertSame(b, typed(b, "personal"))
    }

    @Test
    fun `a bill rule never turns a payment into a bill and a notice stays put`() {
        val m = Verdict.Sure(debit, 1f)
        assertSame(m, typed(m, "bill"))
        assertSame(unknown, typed(unknown, "bill"))
        listOf(
            debit,
            Event.Credit(100, "INR", null, null, null, Mode.Other),
            Event.CardSpend(100, "INR", "1234", null, "ACME"),
        ).forEach { e -> listOf("bill", "income", "expense").forEach { assertEquals(e, typed(Verdict.Sure(e, 1f), it, raw).event) } }
    }

    @Test
    fun `expense and income rules only confirm money and never flip it`() {
        val m = Verdict.Unsure(debit, 0.4f)
        val v = typed(m, "expense")
        assertIs<Verdict.Sure>(v)
        assertEquals(debit, v.event)
        assertSame(m, typed(m, "income"))
        val credit = Verdict.Sure(Event.Credit(100, "INR", null, null, null, Mode.Other), 1f)
        assertSame(credit, typed(credit, "expense"))
        assertSame(unknown, typed(unknown, "expense"))
        assertSame(unknown, typed(unknown, "income"))
    }

    @Test
    fun `a delivery rule still files an alert as a delivery`() {
        val v = typed(Verdict.Sure(Event.Alert, 1f), "delivery", raw)
        assertIs<Verdict.Sure>(v)
        assertIs<Event.Delivery>(v.event)
    }

    @Test
    fun `no type rule ever creates a code or touches one with the new labels`() {
        listOf("delivery", "bill", "expense", "income", "personal").forEach { assertSame(otp, typed(otp, it, raw)) }
        assertSame(unknown, typed(unknown, "otp"))
    }

    @Test
    fun `a money event is never turned into promo spam or alert`() {
        val m = Verdict.Sure(debit, 1f)
        listOf("promo", "spam", "alert", "delivery").forEach { assertSame(m, typed(m, it, raw)) }
    }

    @Test
    fun `sender rules need three in a row`() {
        var n = 0
        var l: String? = null
        listOf("promo", "promo", "promo").forEach {
            n = Senders.bump(l, n, it)
            l = it
        }
        assertEquals(Senders.MIN, n)
        assertEquals(1, Senders.bump("promo", 2, "spam"))
    }

    @Test
    fun `sender keys follow the brand and ignore route prefixes`() {
        assertEquals(Senders.key("VM-ACMEBK-S"), Senders.key("AX-ACMEBK"))
        assertNotEquals(Senders.key("VM-ACMEBK"), Senders.key("VM-ZETABK"))
        assertEquals("Acmebk", Senders.name("acmebk"))
    }

    @Test
    fun `only soft labels count for a sender`() {
        listOf("promo", "spam", "alert", "personal", "delivery").forEach { assertTrue(Senders.counts(it), it) }
        listOf("expense", "income", "bill", "otp").forEach { assertFalse(Senders.counts(it), it) }
    }

    @Test
    fun `a sender rule files soft notices and never money codes or bills`() {
        val v = Senders.apply(unknown, "promo")
        assertIs<Verdict.Sure>(v)
        assertEquals(Event.Promo, v.event)
        assertSame(otp, Senders.apply(otp, "promo"))
        val m = Verdict.Sure(debit, 1f)
        assertSame(m, Senders.apply(m, "promo"))
        val b = Verdict.Sure(Event.Bill(100, "INR", null, null, "ACME", null), 1f)
        assertSame(b, Senders.apply(b, "alert"))
        assertSame(unknown, Senders.apply(unknown, "expense"))
        assertSame(unknown, Senders.apply(unknown, null))
    }
}
