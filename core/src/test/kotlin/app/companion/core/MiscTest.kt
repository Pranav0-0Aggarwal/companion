package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MiscTest {
    @Test
    fun `delivered`() {
        val e = assertIs<Event.Delivery>(extract(sms("AMAZON", "Your Amazon order #408-1234567-1234567 has been delivered.")))
        assertEquals(Stage.Delivered, e.stage)
        assertEquals("Amazon", e.merchant)
    }

    @Test
    fun `out for delivery`() {
        val e = assertIs<Event.Delivery>(extract(sms("VM-DLVRY", "Your package is out for delivery today. Delhivery AWB 12345678901.")))
        assertEquals(Stage.Out, e.stage)
    }

    @Test
    fun `train booking`() {
        val e = assertIs<Event.Travel>(extract(sms("IRCTC", "Your IRCTC ticket PNR 4521896532 is confirmed for 12 Oct 2026, train 12951.")))
        assertEquals("Train", e.what)
        assertEquals(day(2026, 10, 12), e.date)
    }

    @Test
    fun `flight booking mail`() {
        val e = assertIs<Event.Travel>(extract(mail("noreply@goindigo.in", "Booking confirmed", "Your flight 6E 2145 DEL to BLR on 15 Oct is confirmed. Web check-in opens 48 hours before.")))
        assertEquals("Flight", e.what)
        assertEquals(day(2026, 10, 15), e.date)
    }

    @Test
    fun `promo with amount stays promo`() {
        assertIs<Event.Promo>(extract(sms("AD-SHOPAP", "Flat 50% OFF on your next order! Get Rs 500 cashback. Use code SAVE50. T&C apply")))
    }

    @Test
    fun `cashback credit is money`() {
        assertIs<Event.Credit>(extract(notif("com.dreamplug.androidapp", "Cashback", "Cashback of ₹150 credited to your CRED wallet")))
    }

    @Test
    fun `receipt email becomes a debit`() {
        val e = assertIs<Event.Debit>(extract(mail("noreply@uber.com", "Your Uber trip receipt", "Thanks for riding. Total ₹386.50")))
        assertEquals(38650L, e.paise)
        assertEquals("Uber", e.merchant)
    }

    @Test
    fun `whatsapp is personal and sms from a number too`() {
        assertIs<Event.Personal>(extract(Raw(Source.Wa, "Ravi", "Ravi", "Dinner tomorrow at 8?", NOW)))
        assertIs<Event.Personal>(extract(Raw(Source.Ig, "riya", "riya", "send the pic?", NOW)))
        assertIs<Event.Personal>(extract(sms("+919812345678", "Reach by 9 pls")))
    }

    @Test
    fun `unknown business text`() {
        assertIs<Event.Unknown>(extract(sms("VM-POLICY", "Your policy renewal reminder")))
    }
}
