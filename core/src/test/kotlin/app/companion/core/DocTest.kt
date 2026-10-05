package app.companion.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocTest {
    private val ref = LocalDate.of(2026, 10, 5)
    private val ins = "Dear customer, your HDFC ERGO policy no. P0012345678 for vehicle KA01AB1234 is due for renewal on 15-Nov-2026. Sum insured Rs 5,00,000."
    private val range = "Your Bajaj Allianz motor policy 1234567890123 is valid from 01/12/2025 to 30/11/2026."
    private val rc = "Your vehicle registration certificate for KA 01 AB 1234 is valid till 20-Aug-2041."
    private val puc = "PUC certificate for vehicle MH12DE3456 valid till 04/04/2027. Drive safe."
    private val tag = "FASTag activated for vehicle KA03MN9876. Tag ID 34161FA820328EE0D5A1B0C2 issued."
    private val loan = "Your EMI of Rs 12,345 for HDFC Bank personal loan A/c XX4821 is due on 05-11-2026."
    private val wBought = "Your order is delivered on 01/10/2026. 1 year warranty on Samsung Galaxy M34 is included."
    private val wEnds = "Warranty for your Boat Airdopes ends on 15 Oct 2027."

    private fun f(k: DocKind, t: String) = DocExtract.fields(k, t, ref)
    private fun norm(s: String) = s.trim().replace(Regex("\\s+"), " ")

    private fun verbatim(t: String, d: DocFields) {
        val src = norm(t)
        d.number?.let { assertTrue(src.contains(it), "number $it") }
        d.extra.values.forEach { assertTrue(src.contains(it), "extra $it") }
    }

    @Test
    fun `kinds are detected by rules`() {
        assertEquals(DocKind.Insurance, DocExtract.kind(ins))
        assertEquals(DocKind.Insurance, DocExtract.kind(range))
        assertEquals(DocKind.Rc, DocExtract.kind(rc))
        assertEquals(DocKind.Puc, DocExtract.kind(puc))
        assertEquals(DocKind.Fastag, DocExtract.kind(tag))
        assertEquals(DocKind.Loan, DocExtract.kind(loan))
        assertEquals(DocKind.Warranty, DocExtract.kind(wBought))
        assertEquals(DocKind.Warranty, DocExtract.kind(wEnds))
    }

    @Test
    fun `non documents have no kind`() {
        listOf(
            "Your OTP is 123456", "Rs 500 spent at Corner Cafe on 04-10-26", "FASTag low balance alert for KA03MN9876. Recharge now.",
            "Rs 85 debited for toll on FASTag KA03MN9876", "Pre-approved loan offer of Rs 5,00,000! Apply now", "Get insurance for your family today",
            "", "Your parcel is out for delivery", "Warranty",
        ).forEach { assertNull(DocExtract.kind(it), it) }
    }

    @Test
    fun `insurance fields`() {
        val d = f(DocKind.Insurance, ins)
        assertEquals("HDFC ERGO", d.title)
        assertEquals("P0012345678", d.number)
        assertEquals(LocalDate.of(2026, 11, 15), d.expires)
        assertEquals(mapOf("vehicle" to "KA01AB1234", "sum_insured" to "5,00,000"), d.extra)
        verbatim(ins, d)
    }

    @Test
    fun `insurance period takes the end date`() {
        val d = f(DocKind.Insurance, range)
        assertEquals("Bajaj Allianz", d.title)
        assertEquals("1234567890123", d.number)
        assertEquals(LocalDate.of(2026, 11, 30), d.expires)
    }

    @Test
    fun `insurance falls back to a generic insurer name`() {
        val t = "Your Acme Shield General Insurance Company policy AB-123456 expires on 12 Dec 2026"
        val d = f(DocKind.Insurance, t)
        assertEquals("Acme Shield General Insurance Company", d.title)
        assertEquals("AB-123456", d.number)
        assertEquals(LocalDate.of(2026, 12, 12), d.expires)
        assertEquals("Insurance", f(DocKind.Insurance, "policy 99887766 expires 01/01/2027").title)
    }

    @Test
    fun `registration certificate fields`() {
        val d = f(DocKind.Rc, rc)
        assertEquals("KA 01 AB 1234", d.number)
        assertEquals(LocalDate.of(2041, 8, 20), d.expires)
        assertEquals("Registration certificate", d.title)
        verbatim(rc, d)
    }

    @Test
    fun `vehicle numbers in several shapes`() {
        listOf("KA01AB1234", "KA 01 AB 1234", "KA-01-AB-1234", "DL1CAB1234", "MH12DE3456").forEach {
            assertEquals(it, f(DocKind.Rc, "Registration certificate $it valid till 01-01-2040").number, it)
        }
        listOf("XX01AB1234", "KA01AB12345", "KA01AB123", "ka01ab1234").forEach {
            assertNull(f(DocKind.Rc, "Registration certificate $it").number, it)
        }
    }

    @Test
    fun `pollution certificate fields`() {
        val d = f(DocKind.Puc, puc)
        assertEquals("MH12DE3456", d.number)
        assertEquals(LocalDate.of(2027, 4, 4), d.expires)
        assertEquals("Pollution certificate", d.title)
    }

    @Test
    fun `fastag documents carry the tag id and vehicle`() {
        val d = f(DocKind.Fastag, tag)
        assertEquals("34161FA820328EE0D5A1B0C2", d.number)
        assertEquals(mapOf("vehicle" to "KA03MN9876"), d.extra)
        assertNull(d.expires)
        verbatim(tag, d)
        assertEquals("KA03MN9876", f(DocKind.Fastag, "FASTag issued for KA03MN9876").number)
    }

    @Test
    fun `loan fields`() {
        val d = f(DocKind.Loan, loan)
        assertEquals("HDFC Bank", d.title)
        assertEquals("XX4821", d.number)
        assertEquals(mapOf("emi" to "12,345"), d.extra)
        assertNull(d.expires)
        verbatim(loan, d)
        val t = "Loan account number 123456789012 with Bajaj Finance. EMI amount: INR 4500.50, loan closure date 10-Jan-2029"
        val e = f(DocKind.Loan, t)
        assertEquals("123456789012", e.number)
        assertEquals("Bajaj Finance", e.title)
        assertEquals("4500.50", e.extra["emi"])
        assertEquals(LocalDate.of(2029, 1, 10), e.expires)
    }

    @Test
    fun `warranty fields`() {
        val a = f(DocKind.Warranty, wBought)
        assertEquals("Samsung Galaxy M34 warranty", a.title)
        assertEquals(LocalDate.of(2027, 10, 1), a.expires)
        assertEquals(mapOf("product" to "Samsung Galaxy M34", "period" to "1 year"), a.extra)
        assertNull(a.number)
        verbatim(wBought, a)
        val b = f(DocKind.Warranty, wEnds)
        assertEquals("Boat Airdopes warranty", b.title)
        assertEquals(LocalDate.of(2027, 10, 15), b.expires)
        val c = f(DocKind.Warranty, "Purchased on 10/01/2026. Warranty period: 18 months for your Dyson Fan")
        assertEquals(LocalDate.of(2027, 7, 10), c.expires)
    }

    @Test
    fun `every extracted identifier appears verbatim`() {
        listOf(ins, range, rc, puc, tag, loan, wBought, wEnds).forEach { t ->
            val k = assertNotNull(DocExtract.kind(t), t)
            verbatim(t, f(k, t))
        }
    }

    @Test
    fun `other kind only reads an expiry`() {
        val d = f(DocKind.Other, "Membership valid till 31-Dec-2026, card 998877")
        assertNull(d.number)
        assertEquals(LocalDate.of(2026, 12, 31), d.expires)
        assertEquals("Document", d.title)
    }

    @Test
    fun `masks show the tail only`() {
        assertEquals("••••1234", Mask.number("KA01AB1234"))
        assertEquals("••••1234", Mask.number("KA 01 AB 1234"))
        assertEquals("••••5678", Mask.number("12345678"))
        assertEquals("••••21", Mask.number("4821"))
        assertEquals("••••21", Mask.number("XX4821"))
        assertEquals("••••", Mask.number("12"))
        assertEquals("••••", Mask.number(""))
        assertEquals("••••5678", Mask.number("1234-5678"))
    }

    @Test
    fun `titles hide the number`() {
        assertEquals("Insurance ••••5678", Mask.title(DocKind.Insurance, "P0012345678"))
        assertEquals("Loan", Mask.title(DocKind.Loan, null))
        assertEquals("FASTag", Mask.title(DocKind.Fastag, " "))
        assertTrue(!Mask.title(DocKind.Rc, "KA01AB1234").contains("KA01"))
    }

    @Test
    fun `templates have empty values and grammars match them`() {
        DocKind.entries.forEach { k ->
            val t = DocNux.templates.getValue(k)
            val keys = Json.obj(t).keys
            assertTrue(keys.isNotEmpty())
            assertTrue(Json.obj(t).values.all { it == "" })
            val g = DocNux.grammar(k)
            assertTrue(g.startsWith("root ::= \"{\" ws "))
            keys.forEach { assertTrue(g.contains("\"\\\"$it\\\":\" ws s"), it) }
            assertEquals(keys.size - 1, g.lines().first().split(" \",\" ws ").size - 1)
            assertTrue(g.contains("c{0,60}"))
            assertTrue(DocNux.prefix(k).contains(t))
        }
        assertEquals(listOf("policy_number", "insurer", "expiry_date", "vehicle_number", "sum_insured"), Json.obj(DocNux.templates.getValue(DocKind.Insurance)).keys.toList())
    }

    @Test
    fun `prompt wraps clipped text`() {
        val p = DocNux.prompt(DocKind.Puc, "a <|b|> c")
        assertTrue(p.startsWith(DocNux.prefix(DocKind.Puc)))
        assertTrue(p.endsWith(DocNux.SUFFIX))
        assertTrue(!p.removePrefix(DocNux.prefix(DocKind.Puc)).contains("<|b"))
    }

    @Test
    fun `parse keeps verbatim values and valid dates only`() {
        val json = """{"policy_number": "P0012345678", "insurer": "HDFC ERGO", "expiry_date": "2026-11-15", "vehicle_number": "KA01AB9999", "sum_insured": "5,00,000"}"""
        val m = DocNux.parse(DocKind.Insurance, json, ins)
        assertEquals(mapOf("policy_number" to "P0012345678", "insurer" to "HDFC ERGO", "expiry_date" to "2026-11-15", "sum_insured" to "5,00,000"), m)
    }

    @Test
    fun `parse normalises whitespace and rejects bad input`() {
        val m = DocNux.parse(DocKind.Rc, """{"registration_number": "KA 01  AB 1234", "valid_till": "not a date"}""", "RC KA 01 AB\n1234 valid")
        assertEquals(mapOf("registration_number" to "KA 01 AB 1234"), m)
        assertEquals("20 Aug 2041", DocNux.parse(DocKind.Rc, """{"valid_till": "20 Aug 2041"}""", "x")["valid_till"])
        assertTrue(DocNux.parse(DocKind.Rc, "not json", rc).isEmpty())
        assertTrue(DocNux.parse(DocKind.Rc, "[]", rc).isEmpty())
        assertTrue(DocNux.parse(DocKind.Rc, """{"registration_number": ""}""", rc).isEmpty())
        assertTrue(DocNux.parse(DocKind.Rc, """{"registration_number": 12}""", rc).isEmpty())
        assertTrue(DocNux.parse(DocKind.Rc, """{"registration_number": "${"K".repeat(70)}"}""", "K".repeat(70)).isEmpty())
        assertTrue(DocNux.parse(DocKind.Loan, """{"valid_till": "20 Aug 2041"}""", "20 Aug 2041").isEmpty())
    }

    @Test
    fun `parsed maps become fields`() {
        val m = DocNux.parse(DocKind.Insurance, """{"policy_number": "P0012345678", "insurer": "HDFC ERGO", "expiry_date": "2026-11-15", "vehicle_number": "KA01AB1234"}""", ins)
        val d = DocNux.fields(DocKind.Insurance, m, ref)
        assertEquals("HDFC ERGO", d.title)
        assertEquals("P0012345678", d.number)
        assertEquals(LocalDate.of(2026, 11, 15), d.expires)
        assertEquals(mapOf("vehicle_number" to "KA01AB1234"), d.extra)
        val t = DocNux.fields(DocKind.Fastag, mapOf("vehicle_number" to "KA03MN9876"), ref)
        assertEquals("KA03MN9876", t.number)
        assertTrue(t.extra.isEmpty())
        val w = DocNux.fields(DocKind.Warranty, mapOf("product" to "Fan", "ends_on" to "12/05/2027"), ref)
        assertEquals("Fan warranty", w.title)
        assertEquals(LocalDate.of(2027, 5, 12), w.expires)
        assertEquals("Document", DocNux.fields(DocKind.Other, emptyMap(), ref).title)
        assertEquals("Loan", DocNux.fields(DocKind.Loan, emptyMap(), ref).title)
    }
}
