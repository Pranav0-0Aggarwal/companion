package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class OtpTest {
    private fun otp(r: Raw) = assertIs<Event.Otp>(extract(r))

    @Test
    fun `leading code with stated validity`() {
        val e = otp(sms("VM-HDFCBK", "123456 is your OTP for login to HDFC Bank NetBanking. Valid for 5 minutes. Do not share with anyone."))
        assertEquals("123456", e.code)
        assertEquals(NOW + 5 * 60_000, e.expiresAt)
        assertEquals("HDFC Bank", e.service)
        assertEquals("login", e.purpose)
    }

    @Test
    fun `amount in message is not the code`() {
        val e = otp(sms("AX-ICICIB", "Your OTP for txn of Rs.1,200.00 at AMAZON on card ending 4417 is 482913. Valid for 10 mins."))
        assertEquals("482913", e.code)
        assertEquals("payment", e.purpose)
    }

    @Test
    fun `defaults to ten minutes`() {
        val e = otp(sms("AMAZON", "Use 7741 as your one time password for Amazon delivery."))
        assertEquals("7741", e.code)
        assertEquals(NOW + 10 * 60_000, e.expiresAt)
        assertEquals("Amazon", e.service)
        assertEquals("delivery", e.purpose)
    }

    @Test
    fun `google style code`() {
        assertEquals("654321", otp(sms("Google", "G-654321 is your Google verification code.")).code)
    }

    @Test
    fun `expiry in seconds and warnings first`() {
        val e = otp(sms("JD-SWIGGY", "Do not share this OTP with anyone. 8821 is your login code for Swiggy. Expires in 90 seconds"))
        assertEquals("8821", e.code)
        assertEquals(NOW + 90_000, e.expiresAt)
    }

    @Test
    fun `code after colon`() {
        assertEquals("904412", otp(sms("VK-SBIINB", "OTP: 904412 to login to SBI NetBanking. Never share it.")).code)
    }

    @Test
    fun `fraud warning on a debit is not an otp`() {
        val e = extract(sms("VM-HDFCBK", "Rs.500.00 debited from a/c XX1234 on 04-10-26 to VPA shop@okhdfc. Never share OTP or PIN. Call 1930 if not you."))
        assertIs<Event.Debit>(e)
    }

    @Test
    fun `plain text has no otp`() {
        assertNull((extract(sms("VM-HDFCBK", "Your OTP will be sent shortly")) as? Event.Otp))
    }
}
