package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TidyTest {
    private val day = 86_400_000L
    private val now = 100 * day
    private fun ask(kind: String) = Filed(kind, null, null, Refile.ASK)

    @Test
    fun knownBrandSpendIsFiled() {
        assertEquals(Filed("Debit", null, "food", Refile.SETTLED), Tidy.of(ask("Debit"), true, "Zomato", false, now, now))
    }

    @Test
    fun unclearMoneyStays() {
        assertNull(Tidy.of(ask("Debit"), true, null, false, 0, now))
        assertNull(Tidy.of(ask("Debit"), true, "Qwzx Pvt", false, 0, now))
        assertNull(Tidy.of(ask("Credit"), true, "Zomato", true, 0, now))
    }

    @Test
    fun oldInfoSettlesAsAlert() {
        val f = Tidy.of(ask("Unknown"), false, null, false, now - 20 * day, now)
        assertEquals(Kind.Alert.name, f?.kind)
        assertEquals(Refile.SETTLED, f?.state)
        assertNull(Tidy.of(ask("Unknown"), false, null, false, now - 2 * day, now))
    }

    @Test
    fun staleChecksSettle() {
        val c = Filed("Delivery", null, null, Refile.CHECK)
        assertEquals(Refile.SETTLED, Tidy.of(c, false, null, false, now - 5 * day, now)?.state)
        assertNull(Tidy.of(c, false, null, false, now - day, now))
    }
}
