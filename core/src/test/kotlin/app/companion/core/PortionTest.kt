package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortionTest {
    private val dal = Food("dal", "Dal", emptyList(), 118.0, 6.5, 14.0, 4.0, mapOf("bowl" to 150.0, "cup" to 200.0), "bowl", "seed-estimate")
    private val roti = Food("roti", "Roti", emptyList(), 290.0, 9.0, 48.0, 7.0, mapOf("piece" to 40.0), "piece", "seed-estimate")
    private val rice = Food("rice", "Rice", emptyList(), 130.0, 2.7, 28.0, 0.3, mapOf("plate" to 250.0), "plate", "seed-estimate")

    @Test
    fun `units normalise`() {
        listOf("pcs" to "piece", "Pc" to "piece", "nos" to "piece", "Katori" to "bowl", "katoris" to "bowl", "bowls" to "bowl", "thali" to "plate", "gm" to "g", "Grams" to "g", "ml" to "ml", "kg" to "kg", "tbsp" to "tbsp", "teaspoon" to "tsp", "slices" to "slice", "glasses" to "glass", "portion" to "serving", "cups" to "cup")
            .forEach { (a, b) -> assertEquals(b, Portion.unit(a), a) }
        assertNull(Portion.unit("handful"))
        assertNull(Portion.unit(null))
    }

    @Test
    fun `sizes normalise and scale`() {
        assertEquals("small", Portion.size("chhota"))
        assertEquals("large", Portion.size("Bada"))
        assertEquals("medium", Portion.size("regular"))
        assertEquals("small", Portion.size("tall"))
        assertNull(Portion.size("jumbo"))
        assertEquals(0.75, Portion.mult("small"))
        assertEquals(1.0, Portion.mult("medium"))
        assertEquals(1.35, Portion.mult("large"))
        assertEquals(1.0, Portion.mult(null))
    }

    @Test
    fun `quantity words and fractions parse`() {
        listOf(
            "2" to 2.0, "1.5" to 1.5, "half" to 0.5, "aadha" to 0.5, "ek" to 1.0, "do" to 2.0, "teen" to 3.0, "a couple" to 2.0, "a couple of" to 2.0, "1/2" to 0.5,
            "1 1/2" to 1.5, "½" to 0.5, "1½" to 1.5, "one and a half" to 1.5, "dedh" to 1.5, "dhai" to 2.5, "an" to 1.0, " Two " to 2.0, "quarter" to 0.25,
        ).forEach { (a, b) -> assertEquals(b, Portion.qty(a), a) }
        listOf("", "zzz", "0", "1/0", "two zzz").forEach { assertNull(Portion.qty(it), it) }
    }

    @Test
    fun `counts use the food serve map`() {
        assertEquals(Portion(405.0, false), Portion.of(2.0, "bowl", "large", dal))
        assertEquals(Portion(150.0, false), Portion.of(1.0, null, null, dal))
        assertEquals(Portion(112.5, false), Portion.of(1.0, "katori", "small", dal))
        assertEquals(Portion(80.0, false), Portion.of(2.0, "pcs", null, roti))
        assertEquals(Portion(80.0, false), Portion.of(2.0, null, null, roti))
    }

    @Test
    fun `grams and millilitres ignore size`() {
        assertEquals(Portion(200.0, false), Portion.of(200.0, "g", "large", dal))
        assertEquals(Portion(250.0, false), Portion.of(250.0, "ml", null, null))
        assertEquals(Portion(500.0, false), Portion.of(0.5, "kg", null, null))
    }

    @Test
    fun `guessed quantities are flagged fuzzy`() {
        assertEquals(Portion(250.0, true), Portion.of(1.0, "plate", null, null))
        assertEquals(Portion(150.0, true), Portion.of(1.0, "bowl", null, roti))
        assertEquals(Portion(40.0, true), Portion.of(1.0, "handful", null, roti))
        assertTrue(Portion.of(1.0, "bowl", "jumbo", dal).fuzzy)
        assertEquals(Portion(100.0, true), Portion.of(1.0, null, null, null))
        assertEquals(Portion(100.0, true), Portion.of(1.0, "piece", null, null))
        assertEquals(Portion(250.0, true), Portion.of(1.0, "plate", null, dal))
        assertTrue(Portion.of(0.0, "bowl", null, dal).fuzzy)
        assertEquals(150.0, Portion.of(0.0, "bowl", null, dal).grams)
    }

    @Test
    fun `piece falls back to serving then the default unit`() {
        assertEquals(Portion(250.0, true), Portion.of(1.0, "piece", null, rice))
        assertEquals(Portion(30.0, true), Portion.of(1.0, "slice", null, rice))
    }

    @Test
    fun `spoons are known conversions`() {
        assertEquals(Portion(45.0, false), Portion.of(3.0, "tbsp", "large", null))
        assertEquals(Portion(10.0, false), Portion.of(2.0, "tsp", null, null))
        assertFalse(Portion.of(1.0, "tbsp", null, dal).fuzzy)
    }
}
