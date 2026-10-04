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

    @Test
    fun `long gap between cue and code`() {
        assertEquals("111222", otp(sms("VM-ACMEBK-S", "Your verification code, needed to approve the limit change you asked for on the ACME Card, is 111222. Valid 15 minutes.")).code)
    }

    @Test
    fun `code before a dash cue`() {
        assertEquals("555666", otp(sms("VA-ACMEBK-S", "555666-OTP to link your ACME accounts. If you did not start this, contact support.")).code)
    }

    @Test
    fun `secret otp and one time password wording`() {
        assertEquals("777888", otp(sms("JM-ACMEBK-T", "777888 is a SECRET OTP, use it for txn worth INR 111.00 on ACME card XX1234 at Shopco. Valid 2 mins.")).code)
        assertEquals("445566", otp(sms("AD-ACMEBK-T", "445566 is One-Time Password needed for INR 111.00 payment to SHOPCO via ACME Card XX1234.")).code)
    }

    @Test
    fun `order and reference numbers are not codes`() {
        assertEquals("123789", otp(sms("AX-SHOPAP-S", "Your delivery OTP for Order OD000111222333 is 123789. Hand it to the dealer. Valid 30 minutes.")).code)
        assertNull(extract(sms("AX-SHOPAP-S", "Order 481516 is confirmed. Do not share your OTP with anyone.")) as? Event.Otp)
        assertNull(extract(sms("AX-SHOPAP-S", "Call 9812345678 for help. Never share your password.")) as? Event.Otp)
    }

    @Test
    fun `response and coupon codes are not otps`() {
        assertNull(extract(sms("AX-ACMEBK-S", "e-KYC using OTP was successful on 01-06-24. Response code: 1398.")) as? Event.Otp)
        assertNull(extract(sms("VM-SHOPAP-S", "Min purchase of 1655 Code: SAVE20 Online Exclusive")) as? Event.Otp)
        assertNull(extract(sms("VM-SHOPAP-S", "Use code 4821 for 20% off")) as? Event.Otp)
    }

    @Test
    fun `cancellation code is not an otp`() {
        assertNull(extract(sms("AX-SHOPAP-S", "Cancellation code 1111 for order R000 is needed by the partner at the door")) as? Event.Otp)
    }

    @Test
    fun `promotional sender never carries an otp`() {
        assertNull(extract(sms("VM-SHOPAP-P", "123456 is your OTP to claim the offer")) as? Event.Otp)
    }

    @Test
    fun `hindi and hinglish`() {
        assertEquals("482913", otp(sms("VM-ACMEBK-S", "आपका ओटीपी 482913 है। किसी से साझा न करें।")).code)
        assertEquals("482913", otp(sms("VM-ACMEBK-S", "Aapka OTP 482913 hai. Kisi ko na batayein.")).code)
        assertEquals("482913", otp(sms("VM-ACMEBK-S", "482913 aapka OTP hai. Kisi se share na karein.")).code)
        assertEquals("482913", otp(sms("VM-ACMEBK-S", "482913 आपका OTP है")).code)
    }

    @Test
    fun `delivery codes`() {
        val a = otp(sms("AD-SFXDEL-S", "ARRIVING TODAY Item from Shop AWB SF005970. Give CODE 4820 to the delivery rider. Track https://x.example/y"))
        assertEquals("4820", a.code)
        assertEquals("delivery", a.purpose)
        val b = otp(sms("AX-XPBEES-S", "Pickup today for 2 parcels. Tell the rider code 246810 at handover. Rider phone 9000000000."))
        assertEquals("246810", b.code)
        assertEquals("delivery", b.purpose)
        val c = otp(sms("AX-SHOPAP-S", "Share OTP 4821 with the delivery agent to receive your order."))
        assertEquals("4821", c.code)
        assertEquals("delivery", c.purpose)
    }

    @Test
    fun `code with pin and password cues`() {
        assertEquals("3821", otp(sms("VM-ACMEBK-S", "Your login PIN is 3821. Do not share.")).code)
        assertEquals("5512", otp(sms("VM-ACMEBK-S", "Your temporary password: 5512")).code)
    }

    @Test
    fun `postal pin code is not a code`() {
        assertNull(extract(sms("VM-ACMEBK-S", "Delivery to pin code 560001 is delayed. Do not share your OTP.")) as? Event.Otp)
    }
}
