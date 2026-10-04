package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class TxnTest {
    @Test
    fun `credit card spend ignores available limit`() {
        val e = assertIs<Event.CardSpend>(extract(sms("AX-ICICIB", "Rs.386.50 spent on ICICI Bank Card XX4417 on 04-Oct-26 at Uber. Avl Lmt Rs.1,52,000.")))
        assertEquals(38650L, e.paise)
        assertEquals("4417", e.last4)
        assertEquals("ICICI Bank", e.bank)
        assertEquals("Uber", e.merchant)
        assertEquals(Mode.Card, e.mode)
    }

    @Test
    fun `upi debit with vpa merchant`() {
        val e = assertIs<Event.Debit>(extract(sms("VM-HDFCBK", "Rs.240.00 debited from a/c XX1234 on 04-10-26 to VPA bluetokai@okhdfcbank (UPI Ref No 427190123456)")))
        assertEquals(24000L, e.paise)
        assertEquals("1234", e.last4)
        assertEquals("Bluetokai", e.merchant)
        assertEquals(Mode.Upi, e.mode)
        assertEquals("HDFC Bank", e.bank)
    }

    @Test
    fun `sent to name`() {
        val e = assertIs<Event.Debit>(extract(sms("VM-HDFCBK", "Sent Rs.1,500.00 From HDFC Bank A/C *1234 To ROHAN M On 04/10/26 Ref 427190123456 Not You? Call 18002586161")))
        assertEquals(150000L, e.paise)
        assertEquals("1234", e.last4)
        assertEquals("Rohan M", e.merchant)
    }

    @Test
    fun `neft credit with lakh grouping`() {
        val e = assertIs<Event.Credit>(extract(sms("VM-HDFCBK", "INR 1,23,456.00 credited to your A/c XX9032 on 01-Oct-2026 by NEFT from ACME LABS PVT LTD. Avl Bal INR 2,10,000.55")))
        assertEquals(12345600L, e.paise)
        assertEquals("9032", e.last4)
        assertEquals(Mode.Netbanking, e.mode)
        assertEquals("Acme Labs Pvt Ltd", e.merchant)
    }

    @Test
    fun `refund credit with ending digits`() {
        val e = assertIs<Event.Credit>(extract(sms("VM-HDFCBK", "₹1,299.00 refund from Myntra credited to your account ending 9032")))
        assertEquals(129900L, e.paise)
        assertEquals("9032", e.last4)
        assertEquals("Myntra", e.merchant)
    }

    @Test
    fun `bank debit without merchant keeps masked account`() {
        val e = assertIs<Event.Debit>(extract(sms("SBIINB", "A/c X5521 debited by Rs 5,000 on 03Oct26 trf to Rohan M Refno 987654321")))
        assertEquals(500000L, e.paise)
        assertEquals("5521", e.last4)
        assertEquals("Rohan M", e.merchant)
        assertEquals("SBI", e.bank)
    }

    @Test
    fun `whole rupee with inr prefix`() {
        val e = assertIs<Event.Credit>(extract(sms("VM-PYTMPB", "Payment of INR 1234 received from RAHUL via UPI. Ref 1234")))
        assertEquals(123400L, e.paise)
        assertEquals("Rahul", e.merchant)
        assertEquals(Mode.Upi, e.mode)
    }

    @Test
    fun `app notification`() {
        val e = assertIs<Event.Debit>(extract(notif("com.dreamplug.androidapp", "Payment successful", "You paid ₹799 to Airtel using UPI")))
        assertEquals(79900L, e.paise)
        assertEquals("Airtel", e.merchant)
    }

    @Test
    fun `last four formats`() {
        assertEquals("1234", Txn.last4("debited from a/c XX1234"))
        assertEquals("1234", Txn.last4("Card ending 1234 used"))
        assertEquals("1234", Txn.last4("card ending with 1234"))
        assertEquals("1234", Txn.last4("A/C no. XXXXXXXX1234"))
        assertEquals("4417", Txn.last4("on Card **4417 at"))
        assertEquals("9032", Txn.last4("HDFC Bank Card x9032"))
        assertNull(Txn.last4("Call 18002586161 or ref 4271901234"))
    }

    @Test
    fun `amount formats`() {
        assertEquals(123450L, Money.all("Rs.1,234.50")[0].paise)
        assertEquals(123400L, Money.all("INR 1234")[0].paise)
        assertEquals(12345600L, Money.all("₹1,23,456")[0].paise)
        assertEquals(50000L, Money.all("Rs 500/-")[0].paise)
        assertEquals(1250L, Money.all("USD 12.50")[0].paise)
        assertEquals("USD", Money.all("USD 12.50")[0].currency)
        assertEquals(99900L, Money.all("999 INR")[0].paise)
        assertEquals(0, Money.all("5 hours ago, hours 5").size)
    }
}
