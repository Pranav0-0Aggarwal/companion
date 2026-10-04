package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TemplateTest {
    @Test
    fun `numbers do not change the template`() {
        val a = Template.of(sms("VM-ACMEBK-S", "Rs 111.00 spent on card XX1234 at ACME STORE on 04-10-26"))
        val b = Template.of(sms("AX-ACMEBK-S", "Rs 2,450.50 spent on card XX9876 at ACME STORE on 11-12-27"))
        assertEquals(a, b)
        assertEquals(16, a.length)
        assertTrue(a.all { it in "0123456789abcdef" })
    }

    @Test
    fun `different words or brands change the template`() {
        val a = Template.of(sms("VM-ACMEBK-S", "Rs 111.00 spent on card XX1234 at ACME STORE"))
        assertNotEquals(a, Template.of(sms("VM-ACMEBK-S", "Rs 111.00 spent on card XX1234 at OTHER STORE")))
        assertNotEquals(a, Template.of(sms("VM-ZETABK-S", "Rs 111.00 spent on card XX1234 at ACME STORE")))
    }

    @Test
    fun `case and spacing are normalised like the fingerprint`() {
        val a = Template.of(sms("VM-ACMEBK", "Your ACME code is 123456"))
        val b = Template.of(sms("VM-ACMEBK", "your  acme   code is 654321"))
        assertEquals(a, b)
    }

    @Test
    fun `title counts and the source does not`() {
        val a = Template.of(notif("ACME", "Offer", "Rs 111.00 off"))
        assertEquals(a, Template.of(Raw(Source.Sms, "ACME", "Offer", "Rs 999.00 off", NOW + 1)))
        assertNotEquals(a, Template.of(notif("ACME", "Other", "Rs 111.00 off")))
    }
}
