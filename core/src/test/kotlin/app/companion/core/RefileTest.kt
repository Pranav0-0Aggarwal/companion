package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RefileTest {
    private val ask = Filed("Unknown", null, null, Refile.ASK)
    private val settled = Filed("Debit", null, "food", Refile.SETTLED)
    private val sure = Filed("Alert", "spam", null, Refile.SETTLED)

    private fun locked(f: Filed, named: Boolean = false, corrected: Boolean = false, ruled: Boolean = false) = Refile.locked(f, named, corrected, ruled)

    private fun run(old: Filed, new: Filed, corrected: Boolean = false, ruled: Boolean = false) =
        if (locked(old, true, corrected, ruled) || !Refile.moved(old, new)) old else new

    @Test
    fun `an owner correction is never overwritten`() {
        assertTrue(locked(ask, corrected = true))
        assertTrue(locked(settled, corrected = true))
        assertEquals(settled, run(settled, sure, corrected = true))
    }

    @Test
    fun `a learned rule is never overwritten`() {
        assertTrue(locked(ask, ruled = true))
        assertEquals(settled, run(settled, ask, ruled = true))
    }

    @Test
    fun `a paid bill is never overwritten`() {
        val paid = Filed("Bill", null, "bills", Refile.PAID)
        assertTrue(locked(paid))
        assertEquals(paid, run(paid, ask))
    }

    @Test
    fun `plain model output is refiled`() {
        assertFalse(locked(ask))
        assertFalse(locked(settled))
        assertEquals(sure, run(ask, sure))
        assertEquals(ask, run(settled, ask))
    }

    @Test
    fun `a dismissed check item stays dismissed`() {
        assertTrue(Refile.dismissed(Filed("Personal", null, null, Refile.SETTLED), false))
        assertTrue(Refile.dismissed(Filed("Debit", null, "other", Refile.SETTLED), true))
        assertFalse(Refile.dismissed(Filed("Debit", null, "other", Refile.SETTLED), false))
        assertFalse(Refile.dismissed(Filed("Debit", null, "food", Refile.SETTLED), true))
        assertFalse(Refile.dismissed(Filed("Personal", null, null, Refile.CHECK), false))
        assertFalse(Refile.dismissed(Filed("Alert", null, null, Refile.SETTLED), true))
    }

    @Test
    fun `an unchanged item is not counted`() {
        assertFalse(Refile.moved(settled, settled.copy()))
        assertTrue(Refile.moved(settled, settled.copy(category = "bills")))
        assertTrue(Refile.moved(settled, settled.copy(tags = "promo")))
    }

    @Test
    fun `codes never become other kinds and back`() {
        val otp = Filed("Otp", null, null, Refile.SETTLED)
        assertFalse(Refile.moved(otp, otp.copy(kind = "Alert")))
        assertFalse(Refile.moved(sure, sure.copy(kind = "Otp")))
        assertFalse(Refile.moved(sure, sure.copy(tags = "spam,otp")))
        assertFalse(Refile.moved(sure.copy(tags = "otp"), sure))
    }

    @Test
    fun `only newly unsure items need the owner`() {
        assertTrue(Refile.asks(settled, settled.copy(state = Refile.ASK)))
        assertFalse(Refile.asks(ask, ask.copy(tags = "x")))
        assertFalse(Refile.asks(ask, sure))
        assertFalse(Refile.asks(settled, settled))
    }

    @Test
    fun `a rules only guess is not usable`() {
        val e = Event.Unknown
        assertFalse(Refile.usable(Verdict.Unsure(e, 0.4f)))
        assertTrue(Refile.usable(Verdict.Unsure(e, 0.4f, Guess("alert", 0.9f))))
        assertTrue(Refile.usable(Verdict.Sure(e, 0.98f)))
    }

    @Test
    fun `the summary reads like the owner would say it`() {
        assertEquals("312 refiled, 18 now need you", Refile.summary(312, 18, 0))
        assertEquals("5 refiled", Refile.summary(5, 0, 0))
        assertEquals("Nothing changed", Refile.summary(0, 0, 0))
        assertEquals("12 refiled, 4 skipped", Refile.summary(12, 0, 4))
        assertEquals("Nothing changed, 3 skipped", Refile.summary(0, 0, 3))
    }

    @Test
    fun `the import summary counts reads and new items`() {
        assertEquals("Read 4210 messages, 612 new, 9 need you", Refile.imported(4210, 612, 9))
        assertEquals("Read 1 message, 0 new", Refile.imported(1, 0, 0))
    }
}
