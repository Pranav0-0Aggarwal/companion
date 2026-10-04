package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class BillTest {
    @Test
    fun `card statement sms`() {
        val e = assertIs<Event.Statement>(extract(sms("VM-HDFCBK", "Your HDFC Bank Credit Card XX9032 statement is generated. Total Amt Due Rs.18,420.00, Min Amt Due Rs.920.00, Due Date 06-Oct-2026")))
        assertEquals(1842000L, e.paise)
        assertEquals(92000L, e.minPaise)
        assertEquals(day(2026, 10, 6), e.due)
        assertEquals("9032", e.last4)
        assertEquals("HDFC Bank", e.bank)
    }

    @Test
    fun `postpaid bill`() {
        val e = assertIs<Event.Bill>(extract(sms("AD-AIRTEL", "Your Airtel postpaid bill of Rs 799.00 for 9876543210 is due on 09-Oct-2026. Pay now to avoid late fee")))
        assertEquals(79900L, e.paise)
        assertEquals(day(2026, 10, 9), e.due)
        assertEquals("Airtel", e.biller)
    }

    @Test
    fun `due date without year`() {
        val e = assertIs<Event.Bill>(extract(sms("BESCOM", "Dear Customer, your electricity bill of Rs.1,250 is due on 12 Oct. Pay at bescom.in")))
        assertEquals(125000L, e.paise)
        assertEquals(day(2026, 10, 12), e.due)
    }

    @Test
    fun `year rolls over`() {
        val e = assertIs<Event.Bill>(extract(sms("BESCOM", "Your bill of Rs.900 is due on 5th Jan.", NOW)))
        assertEquals(day(2027, 1, 5), e.due)
    }

    @Test
    fun `paid confirmation is not a bill`() {
        val e = extract(sms("AD-AIRTEL", "Thank you for payment of Rs 799.00 towards your Airtel bill."))
        assertNull(e as? Event.Bill)
    }

    @Test
    fun `statement email`() {
        val e = assertIs<Event.Statement>(extract(mail("statements@icicibank.com", "Your ICICI Bank Credit Card statement", "Statement for card ending 4417. Total amount due Rs.31,560.00. Minimum amount due Rs.1,580.00. Pay by 31/10/2026.")))
        assertEquals(3156000L, e.paise)
        assertEquals(158000L, e.minPaise)
        assertEquals(day(2026, 10, 31), e.due)
        assertEquals("4417", e.last4)
    }
}
