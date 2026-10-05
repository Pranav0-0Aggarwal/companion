package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LabelsRetypeTest {
    private fun filed(kind: Kind, category: String? = null, state: String = Refile.ASK) = Filed(kind.name, "tag", category, state)

    @Test
    fun `soft labels retype non-money kinds`() {
        listOf("delivery" to Kind.Delivery, "alert" to Kind.Alert, "promo" to Kind.Promo, "spam" to Kind.Spam, "personal" to Kind.Personal).forEach { (l, k) ->
            assertEquals(k, Labels.retype(l, Kind.Unknown, 0), l)
            assertEquals(k, Labels.retype(l, Kind.Bill, 500), l)
        }
    }

    @Test
    fun `money is never retyped to soft labels and a code is never retyped`() {
        listOf("delivery", "alert", "promo", "spam", "personal").forEach { l ->
            listOf(Kind.Debit, Kind.Credit, Kind.CardSpend).forEach { assertNull(Labels.retype(l, it, 100), "$l $it") }
        }
        Labels.pick.forEach { assertNull(Labels.retype(it, Kind.Otp, 100), it) }
    }

    @Test
    fun `money labels need an amount`() {
        listOf("expense", "income", "bill").forEach {
            assertNull(Labels.retype(it, Kind.Unknown, 0), it)
            assertTrue(Labels.needsAmount(it, Kind.Unknown, 0), it)
        }
        assertEquals(Kind.Debit, Labels.retype("expense", Kind.Unknown, 100))
        assertEquals(Kind.Credit, Labels.retype("income", Kind.Alert, 100))
        assertEquals(Kind.Bill, Labels.retype("bill", Kind.Personal, 100))
        assertFalse(Labels.needsAmount("expense", Kind.Unknown, 100))
        assertFalse(Labels.needsAmount("promo", Kind.Unknown, 0))
        assertFalse(Labels.needsAmount("expense", Kind.Otp, 0))
    }

    @Test
    fun `money flips between expense and income and keeps a card spend`() {
        assertEquals(Kind.Debit, Labels.retype("expense", Kind.Credit, 100))
        assertEquals(Kind.CardSpend, Labels.retype("expense", Kind.CardSpend, 100))
        assertEquals(Kind.Credit, Labels.retype("income", Kind.Debit, 100))
        assertEquals(Kind.Bill, Labels.retype("bill", Kind.Debit, 100))
        assertEquals(Kind.Statement, Labels.retype("bill", Kind.Statement, 100))
    }

    @Test
    fun `refile settles the item and picks a category for money`() {
        val f = Labels.refile("expense", filed(Kind.Unknown), 100)!!
        assertEquals(Filed("Debit", "tag", "other", Refile.SETTLED), f)
        assertEquals("food", Labels.refile("expense", filed(Kind.Unknown), 100, "food")!!.category)
        assertEquals("income", Labels.refile("income", filed(Kind.Debit, "food"), 100)!!.category)
        assertEquals("other", Labels.refile("expense", filed(Kind.Credit, "income"), 100)!!.category)
        assertEquals("shopping", Labels.refile("expense", filed(Kind.Debit, "shopping"), 100)!!.category)
        assertEquals("other", Labels.refile("expense", filed(Kind.Unknown), 100, "income")!!.category)
        assertEquals("bills", Labels.refile("bill", filed(Kind.Debit, "food"), 100)!!.category)
        assertNull(Labels.refile("promo", filed(Kind.Unknown, "food"), 0)!!.category)
    }

    @Test
    fun `refile refuses what retype refuses`() {
        assertNull(Labels.refile("promo", filed(Kind.Debit), 100))
        assertNull(Labels.refile("expense", filed(Kind.Alert), 0))
    }
}
