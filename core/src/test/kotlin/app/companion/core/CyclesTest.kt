package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CyclesTest {
    private val day = 86_400_000L

    private fun slip(d: Long, due: Long? = null, last4: String? = "1234", name: String? = null) = Slip(d * day, due, last4, name)

    private fun groups(vararg s: Slip) = Cycles.of(s.toList()) { it }

    @Test
    fun `same card within the window is one cycle and the newest message leads`() {
        val stmt = slip(100, due = 20_000)
        val note = slip(112, due = 20_005)
        val g = groups(stmt, note)
        assertEquals(1, g.size)
        assertEquals(listOf(note, stmt), g.single())
    }

    @Test
    fun `due dates 12 days apart are two cycles`() {
        assertEquals(2, groups(slip(100, due = 20_000), slip(105, due = 20_012)).size)
        assertEquals(1, groups(slip(100, due = 20_000), slip(105, due = 20_010)).size)
    }

    @Test
    fun `without a due date messages 25 days apart or less chain together`() {
        assertEquals(1, groups(slip(100), slip(120)).size)
        assertEquals(2, groups(slip(100), slip(130)).size)
        assertEquals(1, groups(slip(100, due = 20_000), slip(110)).size)
    }

    @Test
    fun `different cards stay separate`() {
        assertEquals(2, groups(slip(100, last4 = "1111"), slip(101, last4 = "2222")).size)
    }

    @Test
    fun `without a last4 the name is compared ignoring case and spaces`() {
        assertEquals(1, groups(slip(100, last4 = null, name = "HDFC Bank"), slip(101, last4 = null, name = "hdfcbank")).size)
        assertEquals(2, groups(slip(100, last4 = null, name = "HDFC Bank"), slip(101, last4 = null, name = "Axis Bank")).size)
    }

    @Test
    fun `messages with no account never group`() {
        assertEquals(2, groups(slip(100, last4 = null), slip(101, last4 = null)).size)
    }

    @Test
    fun `a chain keeps growing as each message is near the last`() {
        assertEquals(1, groups(slip(100), slip(120), slip(140)).size)
    }

    @Test
    fun `cycles keep the order of their newest message in the input`() {
        val a = slip(130, last4 = "1111")
        val b = slip(100, last4 = "2222")
        val c = slip(115, last4 = "1111")
        assertEquals(listOf(a, b), groups(a, b, c).map { it.first() })
    }

    private val now = 400 * day

    private fun plan(pays: List<Pay> = emptyList(), vararg s: Slip) = Cycles.plan(s.toList(), { it }, pays, now)

    private fun phases(pays: List<Pay> = emptyList(), vararg s: Slip) = plan(pays, *s).map { it.phase }

    @Test
    fun `an older cycle of the same account is closed by the newest one`() {
        val old = slip(300, due = 310)
        val new = slip(350, due = 380)
        val c = plan(emptyList(), new, old)
        assertEquals(listOf(Phase.Open, Phase.Closed), c.map { it.phase })
        assertEquals(Paid.NEWER, Paid.read(c[1].note)?.by)
        assertEquals(350 * day, Paid.read(c[1].note)?.at)
    }

    @Test
    fun `only the newest of three cycles stays open and other accounts are untouched`() {
        val a = slip(300, due = 310)
        val b = slip(330, due = 340)
        val c = slip(360, due = 390)
        val other = slip(365, due = 375, last4 = "9999")
        assertEquals(listOf(Phase.Closed, Phase.Closed, Phase.Open, Phase.Open), phases(emptyList(), a, b, c, other))
    }

    @Test
    fun `cycles with no account are never closed by another`() {
        assertEquals(listOf(Phase.Open, Phase.Open), phases(emptyList(), slip(380, due = 390, last4 = null), slip(381, due = 391, last4 = null)))
    }

    @Test
    fun `a later payment with the same last4 pays the cycle and notes who and when`() {
        val c = plan(listOf(Pay(112 * day + 5, "1234", "HDFC Bank")), slip(100, due = 120))
        assertEquals(Phase.Paid, c.single().phase)
        val n = Paid.read(c.single().note)
        assertEquals(Paid.PAY, n?.by)
        assertEquals("HDFC Bank", n?.name)
        assertEquals(112 * day + 5, n?.at)
    }

    @Test
    fun `a different last4 does not pay even if the bank matches`() {
        assertEquals(listOf(Phase.Old), phases(listOf(Pay(112 * day, "5678", "HDFC Bank")), slip(100, due = 120, name = "HDFC Bank")))
    }

    @Test
    fun `without a last4 the issuer pays`() {
        val s = slip(100, due = 120, last4 = null, name = "SBI Card")
        assertEquals(listOf(Phase.Paid), phases(listOf(Pay(110 * day, null, "SBI")), s))
        assertEquals(listOf(Phase.Paid), phases(listOf(Pay(110 * day, "1234", "SBI Card")), slip(100, due = 120, last4 = null, name = "SBI Card")))
        assertEquals(listOf(Phase.Old), phases(listOf(Pay(110 * day, null, "ICICI Bank")), s))
        assertEquals(listOf(Phase.Old), phases(listOf(Pay(110 * day, null, null)), s))
    }

    @Test
    fun `a payment before the newest message of the cycle does not pay it`() {
        val c = Cycles.plan(listOf(slip(100, due = 120), slip(115, due = 121)), { it }, listOf(Pay(110 * day, "1234", null)), now)
        assertEquals(Phase.Old, c.single().phase)
        assertEquals(Phase.Paid, Cycles.plan(listOf(slip(100, due = 120)), { it }, listOf(Pay(110 * day, "1234", null)), now).single().phase)
    }

    @Test
    fun `a payment pays only the cycle before it`() {
        val a = slip(300, due = 310)
        val b = slip(330, due = 380)
        val c = plan(listOf(Pay(320 * day, "1234", null)), b, a)
        assertEquals(listOf(Phase.Open, Phase.Paid), c.map { it.phase })
    }

    @Test
    fun `the earliest matching payment is the one noted`() {
        val c = plan(listOf(Pay(130 * day, "1234", "Axis"), Pay(120 * day, "1234", "HDFC")), slip(100, due = 125))
        assertEquals("HDFC", Paid.read(c.single().note)?.name)
    }

    @Test
    fun `due more than 45 days ago is old and 45 is still overdue`() {
        assertEquals(listOf(Phase.Open), phases(emptyList(), slip(350, due = 355)))
        assertEquals(listOf(Phase.Open), phases(emptyList(), slip(300, due = now / day - 45)))
        assertEquals(listOf(Phase.Old), phases(emptyList(), slip(300, due = now / day - 46)))
    }

    @Test
    fun `without a due date the newest message must be over 60 days old`() {
        assertEquals(listOf(Phase.Open), phases(emptyList(), slip(340)))
        assertEquals(listOf(Phase.Old), phases(emptyList(), slip(339)))
    }

    @Test
    fun `a bill marked still due is never old`() {
        assertEquals(listOf(Phase.Open), phases(emptyList(), Slip(100 * day, 110, "1234", null, kept = true)))
    }

    @Test
    fun `closed notes round trip`() {
        assertEquals(Paid.ME, Paid.read(Paid.note(Paid.ME, 5))?.by)
        assertEquals("A B", Paid.read(Paid.note(Paid.PAY, 7, "A|B"))?.name)
        assertEquals(null, Paid.read(""))
        assertEquals(null, Paid.read(Paid.KEEP))
    }

    @Test
    fun `a card payment credit carries the card and the bank`() {
        val e = Event.Credit(500000, "INR", "1234", "HDFC Bank", null, Mode.Card)
        assertEquals(Pay(7, "1234", "HDFC Bank"), Pays.of(e, "", 7))
    }

    @Test
    fun `a debit towards a card carries the card digits it names or the issuer it pays`() {
        val a = Event.Debit(500000, "INR", "9999", "ICICI Bank", null, Mode.Netbanking)
        assertEquals(Pay(7, "1234", null), Pays.of(a, "Rs 5000 debited from a/c XX9999 towards card ending 1234", 7))
        val b = Event.Debit(500000, "INR", "9999", "ICICI Bank", "HDFC Bank Card", Mode.Upi)
        assertEquals(Pay(7, null, "HDFC Bank"), Pays.of(b, "Rs 5000 paid via UPI", 7))
        val c = Event.Debit(500000, "INR", "9999", "ICICI Bank", "CRED", Mode.Upi)
        assertEquals(Pay(7, null, null), Pays.of(c, "Rs 5000 paid", 7))
    }
}
