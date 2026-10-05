package app.companion.core

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardFindTest {
    private val utc = ZoneOffset.UTC

    private fun ms(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(utc).toInstant().toEpochMilli()

    private fun due(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    private fun stmt(bank: String?, last4: String?, at: Long, due: Long?, text: String = "") = Seen("Statement", bank, last4, at, due, text)

    private fun spend(bank: String?, last4: String?, at: Long, text: String = "") = Seen("CardSpend", bank, last4, at, null, text)

    private fun find(vararg s: Seen) = CardFind.of(s.toList(), utc)

    @Test
    fun `limits read the credit and available limit`() {
        val a = CardFind.limits("Your card ending 6203 has total credit limit of Rs.1,50,000. Available credit limit Rs.1,12,500.")
        assertEquals(15_000_000L, a.credit)
        assertEquals(11_250_000L, a.avail)
        val b = CardFind.limits("Avl Cr Limit: INR 45,000.50 on card xx5821")
        assertNull(b.credit)
        assertEquals(4_500_050L, b.avail)
        val c = CardFind.limits("Credit Limit Rs 2,00,000 | Avl limit Rs 90,000")
        assertEquals(20_000_000L, c.credit)
        assertEquals(9_000_000L, c.avail)
        val d = CardFind.limits("Spent Rs 500 at Swiggy")
        assertNull(d.credit)
        assertNull(d.avail)
    }

    @Test
    fun `available credit limit is not read as the credit limit`() {
        val l = CardFind.limits("Available credit limit Rs 40,000")
        assertNull(l.credit)
        assertEquals(4_000_000L, l.avail)
    }

    @Test
    fun `a statement gives the card its cycle`() {
        val f = find(
            stmt("ICICI Bank", "6203", ms(2026, 7, 6), due(2026, 7, 26)),
            stmt("ICICI Bank", "6203", ms(2026, 8, 5), due(2026, 8, 25)),
            stmt("ICICI Bank", "6203", ms(2026, 9, 7), due(2026, 9, 27)),
        ).single()
        assertEquals("ICICI Bank", f.bank)
        assertEquals("6203", f.last4)
        assertEquals(6, f.stmtDay)
        assertEquals(26, f.dueDay)
        assertTrue(f.ready)
    }

    @Test
    fun `a statement without a due date infers one and without a statement date from the due`() {
        val a = find(stmt("SBI Card", "2222", ms(2026, 9, 5), null)).single()
        assertEquals(5, a.stmtDay)
        assertEquals(25, a.dueDay)
        val b = find(stmt("SBI Card", "2222", ms(2026, 9, 5), due(2026, 9, 25))).single()
        assertEquals(25, b.dueDay)
    }

    @Test
    fun `card spends alone need two messages and leave the cycle open`() {
        assertEquals(0, find(spend("PNB", "3333", ms(2026, 9, 1))).size)
        val f = find(spend("PNB", "3333", ms(2026, 9, 1)), spend("PNB", "3333", ms(2026, 9, 3))).single()
        assertNull(f.stmtDay)
        assertNull(f.dueDay)
        assertTrue(!f.ready)
    }

    @Test
    fun `debit card and account messages are left out`() {
        assertEquals(0, find(spend("HDFC Bank", "5821", ms(2026, 9, 1), "Spent Rs 400 on HDFC debit card 5821"), spend("HDFC Bank", "5821", ms(2026, 9, 2), "Spent Rs 5 from a/c XX5821")).size)
    }

    @Test
    fun `spends and statements of one card merge and the issuer spelling does not split it`() {
        val f = find(
            spend("SBI", "5555", ms(2026, 9, 1)),
            stmt("SBI Card", "5555", ms(2026, 9, 8), due(2026, 9, 28), "Credit limit Rs 1,00,000"),
            spend("SBI Card", "5555", ms(2026, 9, 9)),
        )
        assertEquals(1, f.size)
        assertEquals("SBI Card", f.single().bank)
        assertEquals(10_000_000L, f.single().limit)
    }

    @Test
    fun `different cards of one bank stay separate and messages without a bank or last4 are ignored`() {
        val f = find(
            stmt("HDFC Bank", "5821", ms(2026, 9, 8), due(2026, 9, 28)),
            stmt("HDFC Bank", "7777", ms(2026, 9, 9), due(2026, 9, 29)),
            stmt(null, "8888", ms(2026, 9, 9), null),
            stmt("Axis Bank", null, ms(2026, 9, 9), null),
        )
        assertEquals(listOf("5821", "7777"), f.map { it.last4 })
    }

    @Test
    fun `the newest limit wins`() {
        val f = find(
            stmt("HDFC Bank", "5821", ms(2026, 8, 8), due(2026, 8, 28), "Credit limit Rs 1,00,000"),
            stmt("HDFC Bank", "5821", ms(2026, 9, 8), due(2026, 9, 28), "Credit limit Rs 1,20,000"),
        ).single()
        assertEquals(12_000_000L, f.limit)
    }
}
