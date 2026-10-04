package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ClassifierTest {
    private val c = RulesClassifier(IST)

    @Test
    fun `otp is sure`() {
        val v = assertIs<Verdict.Sure>(c.classify(sms("VM-HDFCBK", "123456 is your OTP. Valid for 5 minutes.")))
        assertTrue(v.confidence > 0.95f)
    }

    @Test
    fun `identified debit is sure`() {
        assertIs<Verdict.Sure>(c.classify(sms("VM-HDFCBK", "Rs.240.00 debited from a/c XX1234 on 04-10-26 to VPA bluetokai@okhdfcbank (UPI Ref No 427190123456)")))
    }

    @Test
    fun `debit with nothing to identify it asks`() {
        val v = assertIs<Verdict.Unsure>(c.classify(sms("VM-HDFCBK", "Rs 500 debited")))
        assertIs<Event.Debit>(v.event)
    }

    @Test
    fun `business text with an amount asks`() {
        val v = assertIs<Verdict.Unsure>(c.classify(sms("VM-POLICY", "Rs 4,500 premium for your policy needs attention")))
        assertEquals(Event.Unknown, v.event)
    }

    @Test
    fun `business text without money is filed quietly`() {
        assertIs<Verdict.Sure>(c.classify(sms("VM-POLICY", "Your policy renewal reminder")))
    }

    @Test
    fun `bill without due date asks`() {
        assertIs<Verdict.Unsure>(c.classify(sms("AD-AIRTEL", "Your Airtel bill of Rs 799.00 is ready")))
    }

    @Test
    fun `classifier slot accepts other implementations`() {
        val always = Classifier { Verdict.Unsure(Event.Unknown, 0.1f) }
        assertIs<Verdict.Unsure>(always.classify(sms("X", "y")))
    }
}
