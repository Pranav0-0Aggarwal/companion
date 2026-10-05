package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class UpgradeSmsTest {
    private val upi = sms("AD-HDFCBK", "Rs.261.00 debited from a/c **5821 on 03-10-26 to VPA swiggy@icici (UPI Ref No 427712345678). Not you? Call 18002586161")
    private val icici = sms("VM-ICICIT", "INR 979.00 spent using ICICI Bank Card XX6203 on 05-Oct-26 on AMAZON PAY IN G. Avl Limit: INR 1,52,301.00")
    private val stmt = sms("AX-HDFCBK", "Your HDFC Bank Credit Card XX5821 statement is generated. Total due Rs.18,240.00, Min due Rs.910.00, due by 04/11/2026.")
    private val paid = sms("JD-SBICRD", "Payment of Rs 22105.00 received towards your SBI Credit Card ending 7731. Thank you.")
    private val act = sms("AD-ACTGRP", "Dear Patron, your plan is due for auto renewal on 28 Oct. Amount Rs 1,178 will be charged.")

    private fun seen(r: Raw) = extract(r).let { e ->
        when (e) {
            is Event.Move -> Seen(e.kind.name, e.bank, e.last4, r.at, null, r.text())
            is Event.Statement -> Seen(e.kind.name, e.bank, e.last4, r.at, e.due?.toEpochDay(), r.text())
            else -> Seen(e.kind.name, null, null, r.at, null, r.text())
        }
    }

    @Test
    fun `an old merchant name resolves by the live rule`() {
        assertEquals("Amazon Pay", Merchant.resolve("Amazon Pay In G"))
        assertNotEquals("Amazon Pay", Merchant.resolve("Amazon Pay In G", exact = true))
        assertEquals("Rahul Sharma", Merchant.resolve("Rahul Sharma"))
    }

    @Test
    fun `a bill without a biller takes the sender brand`() {
        assertEquals("ACT Fibernet", Merchant.fromSender("AD-ACTGRP"))
        assertNull(Merchant.fromSender("AD-HDFCBK"))
        assertEquals("ACT Fibernet", assertIs<Event.Bill>(extract(act)).biller)
    }

    @Test
    fun `a card bill payment received is filed with confidence`() {
        val e = assertIs<Event.Credit>(extract(paid))
        assertEquals("7731", e.last4)
        assertEquals(Flow.CardBill, Flows.of(e, paid.text()))
        assertIs<Verdict.Sure>(RulesClassifier(IST).classify(paid))
        assertIs<Verdict.Sure>(RulesClassifier(IST).classify(stmt))
    }

    @Test
    fun `a failed card payment stays unsure`() {
        val r = sms("JD-SBICRD", "Payment of Rs 22105.00 received towards your SBI Credit Card ending 7731 was reversed.")
        assertIs<Verdict.Unsure>(RulesClassifier(IST).classify(r))
    }

    @Test
    fun `spent using a bank card is a card spend`() {
        val e = assertIs<Event.CardSpend>(extract(icici))
        assertEquals("6203", e.last4)
        assertEquals("ICICI Bank", e.bank)
        assertIs<Event.CardSpend>(extract(sms("VM-ICICIT", "INR 500.00 debited on ICICI Bank Card XX6203 on 05-Oct-26 at ZOMATO. Avl Limit: INR 1,00,000.00")))
        assertIs<Event.Debit>(extract(upi))
    }

    @Test
    fun `card discovery finds spend limits and card payments`() {
        val found = CardFind.of(listOf(upi, icici, stmt, paid, act).map(::seen), IST).associateBy { it.last4 }
        assertEquals(setOf("5821", "6203", "7731"), found.keys)
        val i = found.getValue("6203")
        assertEquals("ICICI Bank", i.bank)
        assertEquals(15_230_100L, i.avail)
        assertEquals(false, i.ready)
        assertEquals("sbi:7731", found.getValue("7731").key)
        assertEquals(true, found.getValue("5821").ready)
    }

    @Test
    fun `a single spend without a limit is not a card`() {
        assertEquals(emptyList(), CardFind.of(listOf(seen(sms("VM-ICICIT", "INR 979.00 spent using ICICI Bank Card XX6203 on 05-Oct-26 on AMAZON PAY IN G."))), IST))
    }
}
