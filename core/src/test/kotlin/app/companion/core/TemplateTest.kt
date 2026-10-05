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
        assertNotEquals(a, Template.of(sms("VM-ACMEBK-S", "Rs 111.00 refunded on card XX1234 at ACME STORE")))
        assertNotEquals(a, Template.of(sms("VM-ZETABK-S", "Rs 111.00 spent on card XX1234 at ACME STORE")))
    }

    @Test
    fun `the hash is versioned`() {
        val r = sms("VM-ACMEBK-S", "Rs 111.00 spent")
        val v1 = java.security.MessageDigest.getInstance("SHA-256").digest("acmebk|rs0spent".toByteArray()).take(8).joinToString("") { "%02x".format(it) }
        assertNotEquals(v1, Template.of(r))
    }

    private fun m(t: String) = Template.mask(t)

    @Test
    fun `urls are masked`() {
        assertEquals("track <u> now", m("track https://acme.example/t/ab12cd now"))
        assertEquals("open <u> soon", m("open www.acme.example/x?y=1 soon"))
        assertEquals("go <u>", m("go bit.ly/3AbCd"))
    }

    @Test
    fun `emails and vpas are masked`() {
        assertEquals("paid <id> ok", m("paid ravi.k@okbank ok"))
        assertEquals("mail <id>", m("mail a_b+c@acme.example"))
    }

    @Test
    fun `mixed alphanumeric references are masked but plain words and amounts are not`() {
        assertEquals("ref <ref> done", m("ref AB12CD34 done"))
        assertEquals("card <ref>", m("card XX1234"))
        assertEquals("order <ref>", m("order 7ab9z"))
        assertEquals("pay 0 for acme", m("pay 12450.50 for acme"))
        assertEquals("rs0 spent", m("rs111 spent"))
        assertEquals("a0b0 here", m("a1b2 here"))
    }

    @Test
    fun `month and weekday names are masked in full and short form`() {
        assertEquals("<m> <m> <m> <m> <m>", m("january Feb SEPT sep December"))
        assertEquals("on <m>, <m> 0", m("on Tuesday, Oct 5"))
        assertEquals("<m> <m> <m>", m("monday Fri sunday"))
        assertEquals("marching", m("marching"))
    }

    @Test
    fun `digit runs with separators collapse to a single zero`() {
        assertEquals("rs 0 on 0 time 0 cr", m("rs 2,450.50 on 04/10/2026 time 13:45:10 cr"))
        assertEquals("0 and 0", m("04-10-26 and 1.5"))
    }

    @Test
    fun `a payee after a preposition is masked up to a delimiter`() {
        assertEquals("spent at <n> on 0", m("spent at ACME STORE on 04-10-26"))
        assertEquals("sent to <n> via upi", m("sent to Ravi Kumar via upi"))
        assertEquals("from <n>. bal 0", m("from SOME ONE. bal 5"))
        assertEquals("vpa <n> (ref 0)", m("vpa ravi (ref 12)"))
        assertEquals("info <n>", m("info: ACME PAY"))
        assertEquals("to <n> is ok", m("to Ravi Kumar is ok"))
    }

    @Test
    fun `messages differing only in month ref url or payee share a template`() {
        val a = Template.of(sms("VM-ACMEBK-S", "Rs 111.00 debited from A/c XX1234 on 04-Oct-26 to RAVI KUMAR. UPI Ref AB12CD3456. Track https://acme.example/a1"))
        val b = Template.of(sms("AX-ACMEBK-S", "Rs 98,000.00 debited from A/c XX9876 on 11-Nov-27 to SHREE ENTERPRISES. UPI Ref ZZ99YY8877. Track https://acme.example/b2"))
        assertEquals(a, b)
    }

    @Test
    fun `different wording still differs`() {
        val a = Template.of(sms("VM-ACMEBK-S", "Rs 111.00 debited from A/c XX1234 on 04-Oct-26 to RAVI KUMAR"))
        assertNotEquals(a, Template.of(sms("VM-ACMEBK-S", "Rs 111.00 credited to A/c XX1234 on 04-Oct-26 by RAVI KUMAR")))
        assertNotEquals(a, Template.of(sms("VM-ACMEBK-S", "Rs 111.00 debited from A/c XX1234 on 04-Oct-26 to RAVI KUMAR. Not you? Call support")))
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
