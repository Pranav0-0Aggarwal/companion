package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RulesTest {
    private val strict = RulesClassifier(IST, 0.995f)
    private val otp = strict.classify(sms("VM-ACMEBK", "123456 is your OTP. Valid for 5 minutes."))
    private val spend = strict.classify(sms("VM-ACMEBK", "Rs 111.00 spent on card XX1234 at ACME STORE on 04-10-26"))
    private val notice = RulesClassifier(IST).classify(sms("VM-ACMEBK-S", "Your plan renewal summary"))

    @Test
    fun `a rule needs two consistent corrections`() {
        var count = 0
        var label: String? = null
        listOf("food", "food").forEach {
            count = Rules.bump(label, count, it)
            label = it
        }
        assertEquals(Rules.MIN, count)
        assertEquals(2, Rules.bump("food", 1, "food"))
        assertEquals(1, Rules.bump(null, 0, "food"))
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
}
