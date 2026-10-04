package app.companion.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DecideTest {
    private val spec = DecideSpec.fromJson(
        """{"pad_id": 0, "buckets": [8, 16], "tasks": {"kind": {"prefix": [101, 7], "suffix": [102], "labels": ["Promo", "Bill", "Other"]}}}""",
    )
    private val tok = SpTokenizer(listOf("[UNK]", "▁a", "▁b"), listOf(-100f, -1f, -1f), 0)

    @Test
    fun `input is prefix then tokens then suffix padded to the smallest bucket`() {
        val x = Decide.build(spec, "kind", "a b", tok)
        assertEquals(8, x.bucket)
        assertContentEquals(intArrayOf(101, 7, 1, 2, 102, 0, 0, 0), x.ids)
        assertContentEquals(intArrayOf(1, 1, 1, 1, 1, 0, 0, 0), x.mask)
    }

    @Test
    fun `long text moves to the larger bucket and then truncates`() {
        val long = List(10) { "a" }.joinToString(" ")
        val mid = Decide.build(spec, "kind", long, tok)
        assertEquals(16, mid.bucket)
        val huge = Decide.build(spec, "kind", List(100) { "a" }.joinToString(" "), tok)
        assertEquals(16, huge.bucket)
        assertEquals(102, huge.ids[15])
        assertTrue(huge.mask.all { it == 1 })
    }

    @Test
    fun `softmax and top label`() {
        val p = Decide.softmax(floatArrayOf(1f, 2f, 3f))
        assertTrue(abs(p.sum() - 1f) < 1e-5f)
        val (label, prob) = Decide.top(spec, "kind", floatArrayOf(0f, 5f, 0f))
        assertEquals("Bill", label)
        assertTrue(prob > 0.98f)
    }

    private val rules = RulesClassifier(IST)

    @Test
    fun `sure rules are never overridden`() {
        val c = DecideClassifier(rules) { mapOf(Kind.Promo to 0.99f) }
        assertIs<Verdict.Sure>(c.classify(sms("VM-HDFCBK", "123456 is your OTP. Valid for 5 minutes.")))
    }

    @Test
    fun `confident promo settles an unsure message`() {
        val c = DecideClassifier(rules) { mapOf(Kind.Promo to 0.95f, Kind.Unknown to 0.05f) }
        val v = assertIs<Verdict.Sure>(c.classify(sms("VM-POLICY", "Rs 4,500 premium for your policy needs attention")))
        assertEquals(Event.Promo, v.event)
    }

    @Test
    fun `money guesses stay unsure and never auto file`() {
        val c = DecideClassifier(rules) { mapOf(Kind.Debit to 0.99f) }
        val v = assertIs<Verdict.Unsure>(c.classify(sms("VM-POLICY", "Rs 4,500 premium for your policy needs attention")))
        assertTrue(v.confidence < 0.9f)
    }

    @Test
    fun `no model means the rules verdict`() {
        val c = DecideClassifier(rules) { null }
        assertIs<Verdict.Unsure>(c.classify(sms("VM-POLICY", "Rs 4,500 premium for your policy needs attention")))
    }
}
