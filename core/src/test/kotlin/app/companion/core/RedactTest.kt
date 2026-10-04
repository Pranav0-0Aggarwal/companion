package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals

class RedactTest {
    @Test
    fun `codes are redacted when a cue is present`() {
        assertEquals("[redacted] is your OTP for login", Redact.codes("482913 is your OTP for login"))
        assertEquals("Your verification code is [redacted].", Redact.codes("Your verification code is 7741."))
        assertEquals("[redacted] is your Google verification code", Redact.codes("G-654321 is your Google verification code"))
    }

    @Test
    fun `plain amounts survive`() {
        assertEquals("Paid Rs 1,234.50 to Uber", Redact.codes("Paid Rs 1,234.50 to Uber"))
    }
}
