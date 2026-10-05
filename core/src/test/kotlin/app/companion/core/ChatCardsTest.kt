package app.companion.core

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatCardsTest {
    private val z = ZoneId.of("Asia/Kolkata")
    private fun ms(d: LocalDate, h: Int) = d.atTime(h, 0).atZone(z).toInstant().toEpochMilli()

    @Test
    fun `estimate question offers the guess and a typed value`() {
        val o = ChatOpts.of("I couldn't find kadhai paneer. About how many calories, or should I estimate 350?")
        assertEquals(listOf(Opt("~350 kcal (estimate)", "yes"), Opt("Enter value", null)), o)
    }

    @Test
    fun `size question offers three sizes`() {
        val o = ChatOpts.of("Small, medium or large bowl?")
        assertEquals(listOf("Small", "Medium", "Large"), o.map { it.label })
        assertEquals(listOf("Small", "Medium", "Large"), o.map { it.send })
    }

    @Test
    fun `sent options parse as clarification answers`() {
        assertEquals(Answer.Estimate, Clarify.parse("yes", false))
        assertEquals(Answer.Size("small"), Clarify.parse("Small", true))
        assertEquals(emptyList(), ChatOpts.of("What did you have?"))
    }

    @Test
    fun `bar fractions scale to the largest`() {
        val b = listOf(Bar("a", 400), Bar("b", 100), Bar("c", 0))
        assertEquals(listOf(1f, 0.25f, 0f), Bars.fracs(b))
        assertEquals(listOf(0.8f, 0.2f, 0f), Bars.share(b))
        assertEquals(listOf(0f, 0f), Bars.fracs(listOf(Bar("x", 0), Bar("y", 0))))
        assertEquals(emptyList(), Bars.share(emptyList()))
    }

    @Test
    fun `split labels and custom parse`() {
        assertEquals("Full", Split.label(1.0))
        assertEquals("1/2", Split.label(0.5))
        assertEquals("1/3", Split.label(1.0 / 3))
        assertEquals("1/4", Split.label(0.25))
        assertEquals("40%", Split.label(0.4))
        assertEquals(0.4, Split.custom(" 40 "))
        assertEquals(1.0, Split.custom("100"))
        assertNull(Split.custom("0"))
        assertNull(Split.custom("101"))
        assertNull(Split.custom("x"))
    }

    @Test
    fun `trip spends group by local day with shares applied`() {
        val d1 = LocalDate.of(2026, 10, 4)
        val d2 = LocalDate.of(2026, 10, 5)
        val rows = listOf(
            TSpend(1, ms(d2, 9), "Cafe", "food", 90_000, 1.0),
            TSpend(2, ms(d1, 20), "Hotel", "travel", 600_000, 0.5),
            TSpend(3, ms(d1, 8), "Taxi", "travel", 25_001, 1.0),
        )
        val g = TripView.byDay(rows, z)
        assertEquals(listOf(d1, d2), g.map { it.day })
        assertEquals(listOf(3L, 2L), g[0].rows.map { it.id })
        assertEquals(325_001L, g[0].total)
        assertEquals(90_000L, g[1].total)
        assertEquals(emptyList(), TripView.byDay(emptyList(), z))
    }

    @Test
    fun `document expiry state and chip`() {
        val d = LocalDate.of(2027, 3, 9)
        assertEquals(Due.None, DocState.of(null))
        assertEquals(Due.Expired, DocState.of(-1))
        assertEquals(Due.Soon, DocState.of(0))
        assertEquals(Due.Soon, DocState.of(30))
        assertEquals(Due.Ok, DocState.of(31))
        assertNull(DocState.chip(null, null))
        assertEquals("EXPIRED", DocState.chip(-3, d))
        assertEquals("EXPIRES TODAY", DocState.chip(0, d))
        assertEquals("EXPIRES TOMORROW", DocState.chip(1, d))
        assertEquals("IN 12 DAYS", DocState.chip(12, d))
        assertEquals("EXPIRES 9 MAR 27", DocState.chip(120, d))
    }

    @Test
    fun `food source badges`() {
        assertEquals("your food", Origin.Sku.badge)
        assertEquals("your food", Origin.Asked.badge)
        assertEquals("menu value", Origin.Brand.badge)
        assertEquals("menu value", Origin.Db.badge)
        assertEquals("estimate", Origin.Estimate.badge)
    }

    @Test
    fun `card payment titles use the bank`() {
        assertEquals("SBI card payment", CardPay.title("SBI"))
        assertEquals("HDFC card payment", CardPay.title("HDFC Bank"))
        assertEquals("Card card payment", CardPay.title(null))
        assertEquals("Card card payment", CardPay.title(" "))
    }

    @Test
    fun `macro goals fill the calories`() {
        val m = Targets.macros(2000, 70.0)
        assertEquals(84, m.protein)
        assertEquals(62, m.fat)
        assertEquals(277, m.carbs)
        assertEquals(100, Targets.macros(2000, null).protein)
        assertEquals(90, Targets.macros(2000, 70.0, 90).protein)
        assertEquals(0, Targets.macros(300, 100.0).carbs)
    }

    @Test
    fun `open tool lists the new routes`() {
        val s = ToolSpecs.screens
        listOf("today", "money", "food", "inbox", "you", "ledger", "bills", "cards", "trips", "vault", "learn", "settings").forEach { assert(it in s) }
        assert("plan" !in s)
    }
}
