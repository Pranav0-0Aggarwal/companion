package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClarifyTest {
    @Test
    fun calorieAnswers() {
        assertEquals(Answer.Kcal(350.0), Clarify.parse("350", false))
        assertEquals(Answer.Kcal(300.0), Clarify.parse("about 300 cal", false))
        assertEquals(Answer.Kcal(420.0), Clarify.parse("~420 kcal.", false))
    }

    @Test
    fun estimateConfirmations() {
        listOf("yes", "ok", "Haan", "estimate it", "sure thing").forEach { assertEquals(Answer.Estimate, Clarify.parse(it, false), it) }
    }

    @Test
    fun unrelatedTextIsNotAnAnswer() {
        assertNull(Clarify.parse("I had 12 almonds", false))
        assertNull(Clarify.parse("2", false))
        assertNull(Clarify.parse("9999", false))
        assertNull(Clarify.parse("what is my balance", false))
    }

    @Test
    fun sizeAnswers() {
        assertEquals(Answer.Size("large"), Clarify.parse("large", true))
        assertNull(Clarify.parse("350", true))
    }

    @Test
    fun spokenItems() {
        val r = Clarify.spoken("2 roti, 1 bowl dal and rice")
        assertEquals(listOf("roti", "dal", "rice"), r.map { it.name })
        assertEquals(listOf(2.0, 1.0, 1.0), r.map { it.qty })
        assertEquals(listOf(null, "bowl", null), r.map { it.unit })
    }

    @Test
    fun spokenIgnoresEmptyParts() {
        assertEquals(emptyList(), Clarify.spoken(" , + "))
        assertEquals(listOf("poha"), Clarify.spoken("poha,").map { it.name })
    }

    @Test
    fun spokenCapsItems() {
        assertEquals(12, Clarify.spoken((1..20).joinToString(",") { "item$it" }).size)
    }
}
