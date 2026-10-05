package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DupTest {
    @Test
    fun `money kinds group together`() {
        assertTrue(Dup.compatible("Debit", "CardSpend"))
        assertTrue(Dup.compatible("Credit", "Debit"))
        assertTrue(Dup.compatible("CardSpend", "Credit"))
    }

    @Test
    fun `bills and statements group together`() {
        assertTrue(Dup.compatible("Bill", "Statement"))
        assertTrue(Dup.compatible("Statement", "Bill"))
    }

    @Test
    fun `other kinds only match themselves`() {
        assertTrue(Dup.compatible("Travel", "Travel"))
        assertFalse(Dup.compatible("Travel", "Delivery"))
        assertFalse(Dup.compatible("Debit", "Bill"))
        assertFalse(Dup.compatible("Bill", "Travel"))
    }

    @Test
    fun `kinds lists the whole group`() {
        assertEquals(listOf("Debit", "Credit", "CardSpend"), Dup.kinds("Credit"))
        assertEquals(listOf("Travel"), Dup.kinds("Travel"))
    }
}
