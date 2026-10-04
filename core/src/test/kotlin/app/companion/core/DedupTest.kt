package app.companion.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DedupTest {
    private val min = 60_000L

    private fun fp(r: Raw) = assertNotNull(Fingerprint.of(extract(r)))

    private val bankSms = sms("VM-HDFCBK", "Rs.512.00 debited from a/c XX1234 on 04-10-26 to VPA swiggy@icici (UPI Ref No 1)")

    @Test
    fun `same amount and card inside window match`() {
        val a = fp(sms("AX-ICICIB", "Rs.386.50 spent on ICICI Bank Card XX4417 on 04-Oct-26 at Uber."))
        val b = fp(notif("com.dreamplug.androidapp", "Spent", "₹386.50 spent on card ending 4417 at Uber", NOW + 3 * min))
        assertTrue(Fingerprint.same(a, NOW, b, NOW + 3 * min))
    }

    @Test
    fun `outside ten minutes is a new event`() {
        val a = fp(bankSms)
        assertFalse(Fingerprint.same(a, NOW, a, NOW + 11 * min))
        assertTrue(Fingerprint.same(a, NOW, a, NOW + 10 * min))
    }

    @Test
    fun `merchant match without last four`() {
        val a = fp(bankSms)
        val b = fp(notif("com.amazon.pay", "Paid", "You paid ₹512 to Swiggy using UPI"))
        assertTrue(Fingerprint.same(a, NOW, b.copy(last4 = null), NOW + min))
    }

    @Test
    fun `different last four never match`() {
        val a = fp(bankSms)
        val b = a.copy(last4 = "9999")
        assertFalse(Fingerprint.same(a, NOW, b, NOW))
    }

    @Test
    fun `different amount does not match`() {
        val a = fp(bankSms)
        assertFalse(Fingerprint.same(a, NOW, a.copy(paise = a.paise + 1), NOW))
    }

    @Test
    fun `credit and debit never match`() {
        val a = fp(bankSms)
        val c = fp(sms("VM-HDFCBK", "Rs.512.00 credited to a/c XX1234 from Swiggy"))
        assertFalse(Fingerprint.same(a, NOW, c, NOW))
    }

    @Test
    fun `otp seen by sms and notification is one`() {
        val a = fp(sms("VM-HDFCBK", "123456 is your OTP for login."))
        val b = fp(notif("com.hdfc.app", "OTP", "123456 is your OTP for login.", NOW + 5_000))
        assertTrue(Fingerprint.same(a, NOW, b, NOW + 5_000))
    }

    @Test
    fun `statement from sms and mail is one regardless of time`() {
        val a = fp(sms("VM-HDFCBK", "Your HDFC Bank Credit Card XX9032 statement is generated. Total Amt Due Rs.18,420.00, Min Amt Due Rs.920.00, Due Date 06-Oct-2026"))
        val b = fp(mail("alerts@hdfcbank.net", "HDFC Bank Credit Card statement", "Card ending 9032. Total amount due Rs.18,420.00. Minimum amount due Rs.920.00. Due date 06/10/2026"))
        assertTrue(Fingerprint.same(a, NOW, b, NOW + 3 * 24 * 60 * min))
    }

    @Test
    fun `non money events have no fingerprint`() {
        assertNull(Fingerprint.of(Event.Promo))
        assertNull(Fingerprint.of(Event.Unknown))
    }
}
