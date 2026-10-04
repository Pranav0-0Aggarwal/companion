package app.companion.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalibrationTest {
    private val json = """{"version":1,"tasks":{"type":{"temperature":2.0,"labels":["otp","promo","alert"],"sure":{"promo":0.99,"alert":0.9}},"category":{"temperature":0.5,"labels":["food","other"],"sure":{"food":0.95}}}}"""
    private val cal = Calibration.fromJson(json)

    private fun near(a: Float, b: Float) = abs(a - b) < 1e-5f

    @Test
    fun `default is temperature one and 0 97 for every label`() {
        val d = Calibration.DEFAULT
        val x = floatArrayOf(1f, 2f, 3f)
        assertContentEquals(Decide.softmax(x), d.probs("type", x))
        assertEquals(0.97f, d.bar("type", "anything"))
        assertEquals(0.97f, d.bar("category", "food"))
        assertFalse(d.sure("type", "promo", 0.96f))
        assertTrue(d.sure("type", "promo", 0.97f))
    }

    @Test
    fun `temperature divides the logits before softmax`() {
        val x = floatArrayOf(0f, 2f)
        assertTrue(near(cal.probs("type", x)[1], 1f / (1f + Math.exp(-1.0).toFloat())))
        assertTrue(near(cal.probs("category", x)[1], 1f / (1f + Math.exp(-4.0).toFloat())))
        assertTrue(cal.probs("type", x)[1] < Decide.softmax(x)[1])
        assertTrue(cal.probs("category", x)[1] > Decide.softmax(x)[1])
        assertTrue(near(cal.probs("type", x).sum(), 1f))
    }

    @Test
    fun `unknown task keeps temperature one`() {
        val x = floatArrayOf(0f, 2f)
        assertContentEquals(Decide.softmax(x), cal.probs("other", x))
    }

    @Test
    fun `each label has its own bar`() {
        assertEquals(0.99f, cal.bar("type", "promo"))
        assertEquals(0.9f, cal.bar("type", "Alert"))
        assertEquals(0.97f, cal.bar("type", "otp"))
        assertEquals(0.95f, cal.bar("category", "food"))
        assertTrue(cal.sure("type", "alert", 0.95f))
        assertFalse(cal.sure("type", "promo", 0.95f))
        assertFalse(cal.sure("type", "otp", 0.95f))
    }

    @Test
    fun `calibrated probability decides sure from synthetic logits`() {
        val flat = floatArrayOf(5f, 0f, 0f)
        val raw = Decide.softmax(flat)[0]
        assertTrue(Calibration.DEFAULT.sure("type", "otp", raw))
        val p = cal.probs("type", flat)[0]
        assertTrue(p < raw)
        assertFalse(cal.sure("type", "otp", p))
        assertTrue(cal.sure("type", "alert", cal.probs("type", floatArrayOf(0f, 0f, 9f))[2]))
    }

    @Test
    fun `labels must match the model`() {
        assertTrue(cal.fits("type", listOf("OTP", "Promo", "Alert")))
        assertFalse(cal.fits("type", listOf("promo", "otp", "alert")))
        assertFalse(cal.fits("type", listOf("otp", "promo")))
        assertTrue(cal.fits("missing", listOf("a")))
    }

    @Test
    fun `bad files are rejected`() {
        val bad = listOf(
            """{"version":2,"tasks":{}}""",
            """{"tasks":{}}""",
            """{"version":1,"tasks":{"type":{"temperature":0,"labels":[],"sure":{}}}}""",
            """{"version":1,"tasks":{"type":{"temperature":-1,"labels":[],"sure":{}}}}""",
            """{"version":1,"tasks":{"type":{"temperature":1,"labels":["a"],"sure":{"a":1.5}}}}""",
            """{"version":1,"tasks":{"type":{"temperature":1,"labels":["a"],"sure":{"b":0.9}}}}""",
            """{"version":1}""",
            "nope",
        )
        bad.forEach { assertFailsWith<Exception>(it) { Calibration.fromJson(it) } }
    }

    @Test
    fun `labels and bars are optional`() {
        val c = Calibration.fromJson("""{"version":1,"tasks":{"type":{"temperature":1.5}}}""")
        assertEquals(0.97f, c.bar("type", "promo"))
        assertTrue(c.fits("type", listOf("x")))
    }

    private val rules = RulesClassifier(IST)
    private val unsure = sms("VM-ACMEBK-S", "Your plan renewal summary")

    @Test
    fun `classifier uses the per label bar`() {
        val low = Calibration.fromJson("""{"version":1,"tasks":{"type":{"temperature":1,"sure":{"alert":0.9}}}}""")
        val a = DecideClassifier(rules, { low }) { Scored(mapOf("alert" to 0.93f)) }
        assertIs<Verdict.Sure>(a.classify(unsure))
        val b = DecideClassifier(rules) { Scored(mapOf("alert" to 0.93f)) }
        assertIs<Verdict.Unsure>(b.classify(unsure))
    }

    @Test
    fun `a stricter bar keeps a confident guess unsure`() {
        val strict = Calibration.fromJson("""{"version":1,"tasks":{"type":{"temperature":1,"sure":{"alert":0.995}}}}""")
        val c = DecideClassifier(rules, { strict }) { Scored(mapOf("alert" to 0.98f)) }
        val v = assertIs<Verdict.Unsure>(c.classify(unsure))
        assertTrue(v.confidence < 0.9f)
    }

    @Test
    fun `verdict carries the calibrated guess`() {
        val c = DecideClassifier(rules) { Scored(mapOf("alert" to 0.98f, "promo" to 0.01f)) }
        val g = assertNotNull(c.classify(unsure).guess)
        assertEquals("alert", g.label)
        assertEquals(0.98f, g.prob)
        assertNull(rules.classify(sms("VM-HDFCBK", "123456 is your OTP. Valid for 5 minutes.")).guess)
    }

    private val sig = Calibration.fromJson("""{"version":1,"tasks":{"type":{"temperature":2.0,"act":"sigmoid","labels":["otp","promo","alert"],"sure":{"alert":0.8}},"category":{"temperature":1,"labels":["food","other"]}}}""")

    @Test
    fun `act defaults to softmax and unknown tasks too`() {
        assertEquals("softmax", Calibration.DEFAULT.act("type"))
        assertEquals("softmax", cal.act("type"))
        assertEquals("softmax", sig.act("category"))
        assertEquals("sigmoid", sig.act("type"))
        assertEquals("softmax", sig.act("missing"))
    }

    @Test
    fun `bad act is rejected`() {
        assertFailsWith<Exception> { Calibration.fromJson("""{"version":1,"tasks":{"type":{"temperature":1,"act":"relu"}}}""") }
    }

    @Test
    fun `sigmoid divides by temperature and does not normalise`() {
        val p = sig.probs("type", floatArrayOf(2f, 2f, -4f))
        assertTrue(near(p[0], 1f / (1f + Math.exp(-1.0).toFloat())))
        assertTrue(near(p[0], p[1]))
        assertTrue(near(p[2], 1f / (1f + Math.exp(2.0).toFloat())))
        assertTrue(p.sum() > 1f)
        assertTrue(near(sig.probs("type", floatArrayOf(0f, 0f, 0f))[1], 0.5f))
    }

    @Test
    fun `sigmoid is stable for extreme logits`() {
        val p = sig.probs("type", floatArrayOf(1000f, -1000f, 0f))
        assertEquals(1f, p[0])
        assertEquals(0f, p[1])
    }

    @Test
    fun `sigmoid pick takes the argmax and the other labels at half or more`() {
        val p = sig.pick("type", linkedMapOf("otp" to 0.5f, "promo" to 0.49f, "alert" to 0.9f))!!
        assertEquals("alert", p.label)
        assertEquals(listOf("otp"), p.tags)
        assertTrue(p.sure)
    }

    @Test
    fun `sigmoid tags are ordered by probability`() {
        val p = sig.pick("type", linkedMapOf("otp" to 0.6f, "promo" to 0.8f, "alert" to 0.95f))!!
        assertEquals(listOf("promo", "otp"), p.tags)
    }

    @Test
    fun `sigmoid sure follows the primary bar only`() {
        assertFalse(sig.pick("type", linkedMapOf("otp" to 0.1f, "promo" to 0.1f, "alert" to 0.79f))!!.sure)
        val p = sig.pick("type", linkedMapOf("otp" to 0.85f, "promo" to 0.9f, "alert" to 0.6f))!!
        assertEquals("promo", p.label)
        assertFalse(p.sure)
        assertEquals(listOf("otp", "alert"), p.tags)
    }

    @Test
    fun `no label reaches half means no tags and the argmax still wins`() {
        val p = sig.pick("type", linkedMapOf("otp" to 0.2f, "promo" to 0.1f, "alert" to 0.3f))!!
        assertEquals("alert", p.label)
        assertTrue(p.tags.isEmpty())
        assertFalse(p.sure)
    }

    @Test
    fun `softmax pick has no tags and uses the label bar`() {
        val p = cal.pick("type", linkedMapOf("otp" to 0.01f, "promo" to 0.01f, "alert" to 0.98f))!!
        assertEquals("alert", p.label)
        assertTrue(p.tags.isEmpty())
        assertTrue(p.sure)
        assertFalse(cal.pick("type", linkedMapOf("promo" to 0.98f, "alert" to 0.02f))!!.sure)
        assertNull(cal.pick("type", emptyMap()))
    }

    @Test
    fun `sigmoid end to end from logits`() {
        val labels = listOf("otp", "promo", "alert")
        val p = sig.pick("type", labels.zip(sig.probs("type", floatArrayOf(-8f, 3f, 9f)).toList()).toMap())!!
        assertEquals("alert", p.label)
        assertEquals(listOf("promo"), p.tags)
        assertTrue(p.sure)
    }
}
