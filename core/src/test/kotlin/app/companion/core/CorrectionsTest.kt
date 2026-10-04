package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CorrectionsTest {
    @Test
    fun `line has the six fields in order`() {
        val l = Corrections.line("VM-ACMEBK-S", "ACME", "Rs 111.00 at XX1234", null, "type", "promo", 0.93f, "alert")
        assertEquals("""{"sender":"VM-ACMEBK-S","text":"ACME\nRs 111.00 at XX1234","task":"type","model":"promo","modelProb":0.93,"chosen":"alert"}""", l)
        assertFalse(l.contains('\n'))
    }

    @Test
    fun `missing model and sender are explicit`() {
        val m = Json.obj(Corrections.line(null, "ACME", "", null, "category", null, null, "food"))
        assertEquals(mapOf("sender" to "", "text" to "ACME", "task" to "category", "model" to null, "modelProb" to null, "chosen" to "food"), m)
    }

    @Test
    fun `quotes and newlines stay on one line`() {
        val l = Corrections.line("ACME", "say \"hi\"", "a\nb", null, "type", null, null, "alert")
        assertFalse(l.contains('\n'))
        assertEquals("say \"hi\"\na\nb", Json.obj(l)["text"])
    }

    @Test
    fun `type labels cover every kind`() {
        val seen = Kind.entries.map(Types::of).toSet()
        assertEquals(setOf("otp", "expense", "income", "bill", "delivery", "alert", "personal", "promo", "spam"), seen)
    }

    @Test
    fun `body replaces the note when stored`() {
        val l = Corrections.line("ACME", "ACME", "Rs 111.00", "Rs 111.00 spent at ACME on card XX1234", "type", null, null, "expense")
        assertEquals("ACME\nRs 111.00 spent at ACME on card XX1234", Json.obj(l)["text"])
    }

    @Test
    fun `blank body falls back to the note`() {
        assertEquals("ACME\nRs 111.00", Corrections.text("ACME", "Rs 111.00", ""))
        assertEquals("ACME\nRs 111.00", Corrections.text("ACME", "Rs 111.00", null))
    }
}
