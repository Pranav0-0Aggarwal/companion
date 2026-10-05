package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DecideTest {
    private val rules = RulesClassifier(IST)

    private fun scored(vararg p: Pair<String, Float>, cat: Map<String, Float>? = null) = Scored(mapOf(*p), cat)

    @Test
    fun `sure rules are never overridden`() {
        val c = DecideClassifier(rules) { scored("promo" to 0.99f) }
        val v = assertIs<Verdict.Sure>(c.classify(sms("VM-HDFCBK", "123456 is your OTP. Valid for 5 minutes.")))
        assertIs<Event.Otp>(v.event)
    }

    @Test
    fun `a promo guess never erases a code`() {
        val c = DecideClassifier(RulesClassifier(IST, 0.99f)) { scored("promo" to 0.99f) }
        val v = assertIs<Verdict.Unsure>(c.classify(sms("VM-HDFCBK", "123456 is your OTP. Valid for 5 minutes.")))
        assertIs<Event.Otp>(v.event)
    }

    @Test
    fun `confident alert settles an unsure notice`() {
        val c = DecideClassifier(rules) { scored("alert" to 0.98f) }
        val v = assertIs<Verdict.Sure>(c.classify(sms("VM-ACMEBK-S", "Your plan renewal summary")))
        assertEquals(Event.Alert, v.event)
    }

    @Test
    fun `alert guess does not relabel a money event`() {
        val c = DecideClassifier(rules) { scored("alert" to 0.98f) }
        assertIs<Verdict.Unsure>(c.classify(sms("VM-HDFCBK-S", "Rs 450 debited on 04-10-26")))
    }

    @Test
    fun `confident promo settles an unsure message`() {
        val c = DecideClassifier(rules) { scored("promo" to 0.98f, "alert" to 0.02f) }
        val v = assertIs<Verdict.Sure>(c.classify(sms("VM-POLICY", "Rs 4,500 premium for your policy needs attention")))
        assertEquals(Event.Promo, v.event)
    }

    @Test
    fun `money guesses stay unsure and never auto file`() {
        val c = DecideClassifier(rules) { scored("expense" to 0.99f) }
        val v = assertIs<Verdict.Unsure>(c.classify(sms("VM-POLICY", "Rs 4,500 premium for your policy needs attention")))
        assertTrue(v.confidence < 0.9f)
    }

    @Test
    fun `no model means the rules verdict`() {
        val c = DecideClassifier(rules) { null }
        assertIs<Verdict.Unsure>(c.classify(sms("VM-POLICY", "Rs 4,500 premium for your policy needs attention")))
    }

    private val debit = sms("VM-ACMEBK", "Rs 111.00 debited from a/c XX1234 on 04-10-26 to ACME. Txn pending")
    private val unsureDebit = DecideClassifier(rules) { scored("expense" to 0.99f) }

    @Test
    fun `a confident expense settles an unsure debit and keeps the rule fields`() {
        assertIs<Verdict.Unsure>(rules.classify(debit))
        val v = assertIs<Verdict.Sure>(unsureDebit.classify(debit))
        val e = assertIs<Event.Debit>(v.event)
        assertEquals(11_100L, e.paise)
        assertEquals("1234", e.last4)
        assertEquals("expense", v.guess?.label)
    }

    @Test
    fun `a confident income turns the debit into a credit with the same fields`() {
        val v = assertIs<Verdict.Sure>(DecideClassifier(rules) { scored("income" to 0.99f) }.classify(debit))
        val e = assertIs<Event.Credit>(v.event)
        assertEquals(11_100L, e.paise)
        assertEquals("1234", e.last4)
    }

    @Test
    fun `a low expense probability stays unsure with the rule event`() {
        val v = assertIs<Verdict.Unsure>(DecideClassifier(rules) { scored("expense" to 0.9f) }.classify(debit))
        assertIs<Event.Debit>(v.event)
    }

    @Test
    fun `a confident spam settles an unsure notice`() {
        val v = assertIs<Verdict.Sure>(DecideClassifier(rules) { scored("spam" to 0.99f, "alert" to 0.01f) }.classify(sms("VM-ACMEBK-S", "Your plan renewal summary")))
        assertEquals(Event.Spam, v.event)
    }

    @Test
    fun `a spam guess never erases a code`() {
        val v = assertIs<Verdict.Unsure>(DecideClassifier(RulesClassifier(IST, 0.99f)) { scored("spam" to 0.99f) }.classify(sms("VM-HDFCBK", "123456 is your OTP. Valid for 5 minutes.")))
        assertIs<Event.Otp>(v.event)
    }

    @Test
    fun `sigmoid tags ride on the verdict`() {
        val cal = Calibration.fromJson("""{"version":1,"tasks":{"type":{"temperature":1,"act":"sigmoid","sure":{"alert":0.9}}}}""")
        val c = DecideClassifier(rules, { cal }) { scored("alert" to 0.95f, "promo" to 0.7f, "spam" to 0.2f) }
        val v = assertIs<Verdict.Sure>(c.classify(sms("VM-ACMEBK-S", "Your plan renewal summary")))
        assertEquals(listOf("promo"), v.tags)
    }

    @Test
    fun `softmax carries no tags`() {
        val v = assertIs<Verdict.Sure>(DecideClassifier(rules) { scored("alert" to 0.98f, "promo" to 0.02f) }.classify(sms("VM-ACMEBK-S", "Your plan renewal summary")))
        assertTrue(v.tags.isEmpty())
    }

    @Test
    fun `a sure category rides on the verdict and an unsure one is dropped`() {
        val sure = DecideClassifier(rules) { scored("expense" to 0.99f, cat = mapOf("food" to 0.98f, "other" to 0.02f)) }.classify(debit)
        assertEquals("food", sure.cat?.label)
        val weak = DecideClassifier(rules) { scored("expense" to 0.99f, cat = mapOf("food" to 0.6f, "other" to 0.4f)) }.classify(debit)
        assertNull(weak.cat)
    }
}
