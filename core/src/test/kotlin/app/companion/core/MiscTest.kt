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
        assertIs<Event.Unknown>(extract(sms("VM-ACME", "Your plan renewal summary")))
    }

    @Test
    fun `security and booking notices are alerts`() {
        assertIs<Event.Alert>(extract(sms("VM-ACME", "New login to your account from a new device. If this was not you, reset your password.")))
        assertIs<Event.Alert>(extract(sms("VM-ACME", "Your appointment is confirmed for 12 Oct, 10:30 AM.")))
        assertIs<Event.Alert>(extract(sms("VM-ACME", "Beware of fraud calls. Never share your card details with anyone.")))
    }

    @Test
    fun `service sender leftovers are alerts and bare senders stay unknown`() {
        assertIs<Event.Alert>(extract(sms("AD-ACMEBK-S", "Your plan renewal summary")))
        assertIs<Event.Unknown>(extract(sms("AD-ACMEBK", "Your plan renewal summary")))
    }

    @Test
    fun `promotional sender is promo even with a credited word`() {
        assertIs<Event.Promo>(extract(sms("VM-SHOPAP-P", "ADDED: Rs 111 credited to your wallet! Redeem on min purchase of 999")))
    }

    @Test
    fun `service sender with an offer word is not promo`() {
        assertIs<Event.Alert>(extract(sms("VM-ACMEBK-S", "Your credit card offer limit was updated. Visit the app")))
    }

    @Test
    fun `return and refund parcels are not deliveries`() {
        assertIs<Event.Alert>(extract(sms("AD-SHOPAP-S", "Your return for order 1234 has been picked up. Refund will follow.")))
    }

    @Test
    fun `placed and shipped stages`() {
        assertEquals(Stage.Shipped, assertIs<Event.Delivery>(extract(sms("AD-DLHVRY-S", "Your order has been shipped. Track AWB 12345678901"))).stage)
        assertEquals(Stage.Placed, assertIs<Event.Delivery>(extract(sms("AD-SHOPAP-S", "Your order 4821 is confirmed. Tracking id 998877"))).stage)
    }

    @Test
    fun `cashback offers are promo not income`() {
        assertIs<Event.Promo>(extract(sms("AD-SHOPAP", "Get Rs 50 cashback on your first order. Order now")))
        assertIs<Event.Promo>(extract(sms("VM-SHOPAP-P", "Rs 301 cashback is waiting for you. Pay with UPI and claim your reward")))
    }

    @Test
    fun `real cashback credit is still money`() {
        assertIs<Event.Credit>(extract(sms("VM-HDFCBK-S", "Cashback of Rs 50 credited to your a/c XX1234 on 04-10-26")))
    }

    @Test
    fun `security notice with offer words is an alert not promo`() {
        assertIs<Event.Alert>(extract(sms("VM-ACME", "Security alert: new login from a new device. Learn about our offers and fraud tips at acme.example")))
    }
}
