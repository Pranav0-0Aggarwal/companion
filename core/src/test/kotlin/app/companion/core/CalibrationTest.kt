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
        val a = DecideClassifier(rules, { low }) { mapOf(Kind.Alert to 0.93f) }
        assertIs<Verdict.Sure>(a.classify(unsure))
        val b = DecideClassifier(rules) { mapOf(Kind.Alert to 0.93f) }
        assertIs<Verdict.Unsure>(b.classify(unsure))
    }

    @Test
    fun `a stricter bar keeps a confident guess unsure`() {
        val strict = Calibration.fromJson("""{"version":1,"tasks":{"type":{"temperature":1,"sure":{"alert":0.995}}}}""")
        val c = DecideClassifier(rules, { strict }) { mapOf(Kind.Alert to 0.98f) }
        val v = assertIs<Verdict.Unsure>(c.classify(unsure))
        assertTrue(v.confidence < 0.9f)
    }

    @Test
    fun `verdict carries the calibrated guess`() {
        val c = DecideClassifier(rules) { mapOf(Kind.Alert to 0.98f, Kind.Promo to 0.01f) }
        val g = assertNotNull(c.classify(unsure).guess)
        assertEquals("alert", g.label)
        assertEquals(0.98f, g.prob)
        assertNull(rules.classify(sms("VM-HDFCBK", "123456 is your OTP. Valid for 5 minutes.")).guess)
    }
}
