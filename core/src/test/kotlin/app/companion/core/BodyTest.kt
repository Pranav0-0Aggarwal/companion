package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BodyTest {
    private val text = "Rs 111.00 spent on card XX1234 at ACME"
    private val debit = Event.Debit(11_100, "INR", "1234", null, "ACME", Mode.Card)

    @Test
    fun `message text is kept for a normal item`() {
        assertEquals(text, Body.keep(debit, emptyList(), text, NOW, Long.MIN_VALUE))
    }

    @Test
    fun `an otp body is never kept`() {
        assertNull(Body.keep(Event.Otp("123456", NOW, "ACME", null), emptyList(), "123456 is your OTP", NOW, Long.MIN_VALUE))
    }

    @Test
    fun `an otp tag keeps the body out`() {
        assertNull(Body.keep(Event.Alert, listOf("otp"), "123456 is your OTP for ACME", NOW, Long.MIN_VALUE))
    }

    @Test
    fun `a code in a non otp body is redacted`() {
        val b = Body.keep(Event.Alert, emptyList(), "Your ACME login code is 123456", NOW, Long.MIN_VALUE)!!
        assertTrue("123456" !in b)
        assertTrue("[redacted]" in b)
    }

    @Test
    fun `text older than the cutoff is not kept`() {
        assertNull(Body.keep(debit, emptyList(), text, NOW - 10, NOW))
        assertEquals(text, Body.keep(debit, emptyList(), text, NOW, NOW))
    }

    @Test
    fun `blank text is null and long text is capped`() {
        assertNull(Body.keep(debit, emptyList(), "  \n ", NOW, Long.MIN_VALUE))
        assertEquals(Body.MAX, Body.keep(debit, emptyList(), "a".repeat(Body.MAX + 50), NOW, Long.MIN_VALUE)!!.length)
    }

    @Test
    fun `retention windows`() {
        val day = 86_400_000L
        assertEquals(NOW - 90 * day, Body.since(90, NOW))
        assertEquals(NOW - 365 * day, Body.since(365, NOW))
        assertEquals(Long.MIN_VALUE, Body.since(0, NOW))
    }
}
