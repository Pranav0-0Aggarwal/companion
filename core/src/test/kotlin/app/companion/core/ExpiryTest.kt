package app.companion.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExpiryTest {
    private val exp = LocalDate.of(2026, 11, 15)
    private fun left(n: Long) = exp.minusDays(n)

    @Test
    fun `nothing before thirty days`() {
        assertNull(Expiry.due(exp, left(41), emptySet()))
        assertNull(Expiry.due(exp, left(31), emptySet()))
    }

    @Test
    fun `smallest milestone covering the days left`() {
        assertEquals(30, Expiry.due(exp, left(30), emptySet()))
        assertEquals(30, Expiry.due(exp, left(29), emptySet()))
        assertEquals(30, Expiry.due(exp, left(8), emptySet()))
        assertEquals(7, Expiry.due(exp, left(7), emptySet()))
        assertEquals(7, Expiry.due(exp, left(2), emptySet()))
        assertEquals(1, Expiry.due(exp, left(1), emptySet()))
        assertEquals(0, Expiry.due(exp, left(0), emptySet()))
        assertEquals(0, Expiry.due(exp, exp.plusDays(9), emptySet()))
    }

    @Test
    fun `sent milestones are not repeated or revisited`() {
        assertNull(Expiry.due(exp, left(30), setOf(30)))
        assertNull(Expiry.due(exp, left(5), setOf(7)))
        assertEquals(7, Expiry.due(exp, left(5), setOf(30)))
        assertEquals(1, Expiry.due(exp, left(1), setOf(30, 7)))
        assertNull(Expiry.due(exp, left(1), setOf(1)))
        assertNull(Expiry.due(exp, exp.plusDays(3), setOf(0)))
        assertEquals(0, Expiry.due(exp, exp.plusDays(3), setOf(1, 7, 30)))
    }

    @Test
    fun `next is the coming milestone date`() {
        assertEquals(left(30), Expiry.next(exp, left(41), emptySet()))
        assertEquals(left(7), Expiry.next(exp, left(20), setOf(30)))
        assertEquals(left(1), Expiry.next(exp, left(5), setOf(30, 7)))
        assertEquals(exp, Expiry.next(exp, left(1), setOf(30, 7, 1)))
    }

    @Test
    fun `next is today when something is due`() {
        assertEquals(left(5), Expiry.next(exp, left(5), emptySet()))
        assertEquals(exp.plusDays(3), Expiry.next(exp, exp.plusDays(3), emptySet()))
    }

    @Test
    fun `next is null when everything was sent`() {
        assertNull(Expiry.next(exp, exp.plusDays(3), setOf(0)))
        assertNull(Expiry.next(exp, exp, setOf(30, 7, 1, 0)))
    }

    @Test
    fun `copy has no document number`() {
        assertEquals("Insurance (HDFC ERGO) expires in 7 days", Expiry.text(DocKind.Insurance, "HDFC ERGO", 7))
        assertEquals("Insurance (HDFC ERGO) expires in 30 days", Expiry.text(DocKind.Insurance, "HDFC ERGO", 30))
        assertEquals("Pollution expires tomorrow", Expiry.text(DocKind.Puc, "Pollution", 1))
        assertEquals("Registration expires today", Expiry.text(DocKind.Rc, "", 0))
        assertEquals("Warranty (Fan warranty) has expired", Expiry.text(DocKind.Warranty, "Fan warranty", -4))
    }

    @Test
    fun `sent set round trips as a string`() {
        assertEquals("1,7,30", Expiry.mask(setOf(30, 7, 1)))
        assertEquals("", Expiry.mask(emptySet()))
        assertEquals(setOf(1, 7, 30), Expiry.parseSent("1,7,30"))
        assertEquals(setOf(7, 0), Expiry.parseSent("x,2, 7,,0"))
        assertEquals(emptySet(), Expiry.parseSent(""))
        assertEquals(setOf(0, 7), Expiry.parseSent(Expiry.mask(setOf(7, 0))))
    }
}
