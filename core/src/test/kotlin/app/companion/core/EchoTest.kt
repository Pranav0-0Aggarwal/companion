package app.companion.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EchoTest {
    private val h = 3_600_000L
    private fun inn(last4: String?, m: String?) = Fingerprint(Group.In, 250000, last4, m, null, null)

    @Test
    fun appAlertDaysLaterMatchesTheSms() {
        assertTrue(Fingerprint.echo(inn("7713", "aarav"), 0, "Sms", "AX-HDFCBK", inn("7713", null), 42 * h, "Notif", "in.indwealth"))
    }

    @Test
    fun twoSmsStayApart() {
        assertFalse(Fingerprint.echo(inn("7713", "aarav"), 0, "Sms", "AX-HDFCBK", inn("7713", "aarav"), h, "Sms", "AX-HDFCBK"))
    }

    @Test
    fun otherAccountOrTooLateStaysApart() {
        assertFalse(Fingerprint.echo(inn("7713", null), 0, "Sms", "AX-HDFCBK", inn("4021", null), h, "Notif", "in.indwealth"))
        assertFalse(Fingerprint.echo(inn("7713", null), 0, "Sms", "AX-HDFCBK", inn("7713", null), 80 * h, "Notif", "in.indwealth"))
    }

    @Test
    fun repeatedBlankAlertFromOneSenderMatches() {
        assertTrue(Fingerprint.echo(inn(null, null), 0, "Wa", "Shop", inn(null, null), 5 * h, "Wa", "Shop"))
        assertFalse(Fingerprint.echo(inn(null, null), 0, "Wa", "Shop", inn(null, null), 5 * h, "Wa", "Asha"))
    }
}

class PairTest {
    @Test
    fun matchingDebitAndCreditMinutesApartAreSelf() {
        assertTrue(Flows.pair("Debit", 0, "Credit", 60_000))
        assertFalse(Flows.pair("Debit", 0, "Credit", 3_600_000))
        assertFalse(Flows.pair("CardSpend", 0, "Credit", 60_000))
    }
}
