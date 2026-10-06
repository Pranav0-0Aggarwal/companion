package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DupesTest {
    private val h = 3_600_000L
    private fun out(last4: String?, m: String?) = Fingerprint(Group.Out, 11000, last4, m, null, null)

    @Test
    fun readsUpiReferences() {
        assertEquals("612345678901", Dupes.ref("Rs 110 debited from A/c XX4021 UPI Ref No 612345678901"))
        assertEquals("612345678901", Dupes.ref("UPI:612345678901 paid to Aarav"))
        assertNull(Dupes.ref("paid to UPI 9876543210@ybl"))
    }

    @Test
    fun sameReferenceIsOnePaymentEvenAcrossSms() {
        val a = Sig(out("4021", null), 0, "Sms", "AX-HDFCBK", "612345678901")
        val b = Sig(out(null, "aarav"), 5 * h, "Sms", "VM-HDFCBK", "612345678901")
        assertTrue(Dupes.same(a, b))
    }

    @Test
    fun differentReferencesStayApartEvenWhenTheyLookAlike() {
        val a = Sig(out("4021", "amazon"), 0, "Sms", "AX-ICICIB", "612345678901")
        val b = Sig(out("4021", "amazon"), 2 * h, "Notif", "in.indwealth", "612345678902")
        assertFalse(Dupes.same(a, b))
    }

    @Test
    fun withoutReferencesFallsBackToEchoRules() {
        assertTrue(Dupes.same(Sig(out("4021", null), 0, "Sms", "AX-ICICIB", null), Sig(out("4021", null), 30 * h, "Notif", "in.indwealth", null)))
        assertFalse(Dupes.same(Sig(out("4021", null), 0, "Sms", "AX-ICICIB", null), Sig(out("4021", null), 30 * h, "Sms", "AX-ICICIB", null)))
    }
}
