package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GapsTest {
    private val rules = RulesClassifier(IST)

    private fun gaps(sender: String, body: String) = sms(sender, body).let { Gaps.of(it, rules.classify(it).event, IST) }

    private fun of(e: Event, body: String, sender: String = "VK-BANKXY") = Gaps.of(sms(sender, body), e, IST)

    private val debit = Event.Debit(50000, "INR", "1234", null, null, Mode.Upi)

    @Test
    fun `a code with no cue word but a verify intent is a gap`() {
        assertEquals(setOf(Field.OtpCode), gaps("VM-FOOAPP", "Dear customer, 664120 shall be your login credential. Valid for 3 min."))
        assertEquals(setOf(Field.OtpCode), gaps("VM-FOOAPP", "Use 482913 to verify your Foo account. Valid for 5 min."))
    }

    @Test
    fun `an extracted otp has no gap`() {
        val m = sms("VM-FOOAPP", "482913 is your OTP for Foo login")
        assertTrue(Gaps.of(m, rules.classify(m).event, IST).isEmpty())
    }

    @Test
    fun `a cue word without a code token is not a gap`() {
        assertTrue(gaps("VM-FOOAPP", "Your OTP will be sent shortly, please verify your login").isEmpty())
    }

    @Test
    fun `never share otp in a bank alert is not a gap`() {
        assertTrue(gaps("VK-BANKXY", "Rs 500 debited from A/c XX1234 on 04-10-26. Never share OTP, CVV or PIN with anyone. Avl bal Rs 9,000.50").isEmpty())
        assertTrue(of(debit, "Rs 500 debited from A/c XX1234. Never share OTP 123456 with anyone").isEmpty())
    }

    @Test
    fun `amounts dates and refs are not code tokens`() {
        assertTrue(of(Event.Alert, "Your OTP is sent. Txn Rs 5000 ref 123456 on 04-10-2026 call 9876543210").isEmpty())
    }

    @Test
    fun `a pin code or a promo sender never reads as a code`() {
        assertTrue(of(Event.Alert, "Verify your PIN code 560001 on the app").isEmpty())
        assertTrue(of(Event.Alert, "Use code 482913 to verify your login", "AX-OFFERS-P").isEmpty())
        assertTrue(of(Event.Alert, "Flat 20% off, use code 482913 to verify").isEmpty())
    }

    @Test
    fun `chats and structured events never ask for a code`() {
        val chat = Raw(Source.Wa, "Asha", "", "login code 482913 verify", NOW)
        assertTrue(Gaps.of(chat, Event.Personal, IST).isEmpty())
        assertTrue(of(debit, "Rs 500 debited. Use 482913 to verify your login").isEmpty())
    }

    @Test
    fun `money cues with no readable amount are a gap`() {
        assertEquals(setOf(Field.Amount), gaps("VK-BANKXY", "INR.2500 debited from A/c XX1234 at Corner Cafe on 04-10-26. Alert"))
        assertEquals(setOf(Field.Amount), of(Event.Unknown, "You paid 750 at Corner Cafe"))
    }

    @Test
    fun `a readable amount or a move leaves no amount gap`() {
        assertTrue(of(Event.Alert, "Alert: Rs 2500 hold placed on your card").isEmpty())
        assertTrue(of(debit, "INR.2500 debited").isEmpty())
        assertTrue(of(Event.Unknown, "Your order 7788 is packed").isEmpty())
    }

    @Test
    fun `a bill with no due date but a date in the text is a gap`() {
        val bill = Event.Bill(120000, "INR", null, null, "Foo", null)
        assertEquals(setOf(Field.Due), of(bill, "Your Foo bill of Rs 1,200 is ready. Pay by 20 Oct 2026").also { assertTrue(Field.Merchant !in it) })
        assertTrue(of(bill, "Your Foo bill of Rs 1,200 is ready").isEmpty())
        assertTrue(of(bill.copy(due = day(2026, 10, 20)), "Your Foo bill of Rs 1,200 is due 20 Oct 2026").isEmpty())
    }

    @Test
    fun `a statement with no due date but a date in the text is a gap`() {
        val st = Event.Statement(450000, null, null, "1234", "Foo")
        assertEquals(setOf(Field.Due), of(st, "Foo card statement Rs 4,500. Pay before 12/10/2026"))
    }

    @Test
    fun `bill cues with no amount and no due are both gaps`() {
        assertEquals(setOf(Field.Amount, Field.Due), gaps("VK-BANKXY", "Your Foo electricity bill is generated. Pay INR.1200 by 20 Oct 2026"))
    }

    @Test
    fun `a bill without a biller asks for the merchant`() {
        assertEquals(setOf(Field.Merchant), of(Event.Bill(120000, "INR", day(2026, 10, 20), null, null, null), "Bill of Rs 1,200 due 20 Oct 2026"))
    }

    @Test
    fun `a move with no merchant asks only when a payee cue is present`() {
        assertEquals(setOf(Field.Merchant), of(debit, "Rs 500 debited. Paid to Zed Mart via UPI"))
        assertTrue(of(debit, "Rs 500 debited from A/c XX1234 on 04-10-26").isEmpty())
        assertTrue(of(debit, "Rs 500 debited. Paid to Cash Mart via UPI").isEmpty())
        assertTrue(of(debit, "Rs 500 debited from your A/c XX1234 at 10:30").isEmpty())
        val credit = Event.Credit(90000, "INR", "1234", null, null, Mode.Upi)
        assertEquals(setOf(Field.Merchant), of(credit, "Rs 900 credited from Zed Traders"))
        assertTrue(of(credit, "Rs 900 credited to your account").isEmpty())
        assertTrue(of(debit.copy(merchant = "Zed Mart"), "Rs 500 debited. Paid to Zed Mart").isEmpty())
    }

    @Test
    fun `needs covers unsure verdicts and key gaps`() {
        val m = sms("VK-BANKXY", "Rs 500 debited from A/c XX1234 on 04-10-26. Avl bal Rs 9,000.50")
        assertEquals(false, Gaps.needs(m, rules.classify(m), IST))
        val u = sms("VM-FOOAPP", "Use 482913 to verify your Foo account")
        assertEquals(true, Gaps.needs(u, Verdict.Sure(Event.Alert, 1f), IST))
        assertEquals(true, Gaps.needs(m, Verdict.Unsure(debit, 0.5f), IST))
    }
}
