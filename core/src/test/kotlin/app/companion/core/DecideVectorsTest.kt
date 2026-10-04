package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DecideVectorsTest {
    private fun res(n: String) = ClassLoader.getSystemResourceAsStream("decide/$n")!!.readBytes()

    private val tok = SpTokenizer.fromDtk(res("tokenizer.dtk"))
    private val spec = DecideSpec.fromJson(String(res("schema_prefix.json")))
    private val cases = (Json.obj(String(res("inputs.json")))["messages"] as List<*>).map { it as Map<*, *> }

    private fun ints(v: Any?) = (v as List<*>).map { (it as Number).toInt() }

    private fun floats(v: Any?) = (v as List<*>).map { (it as Number).toFloat() }.toFloatArray()

    @Test
    fun `all word and id vectors match the python reference`() {
        val vectors = (Json.obj(String(res("vectors.json")))["vectors"] as List<*>).map { it as Map<*, *> }
        assertEquals(200, vectors.size)
        for (v in vectors) {
            val text = v["text"] as String
            assertEquals((v["words"] as List<*>).map { it as String }, Decide.words(spec, text), "words for $text")
            assertEquals(ints(v["ids"]), Decide.ids(spec, text, tok).toList(), "ids for $text")
        }
    }

    @Test
    fun `full input ids equal the python reference for synthetic messages`() {
        assertEquals(13, cases.size)
        for (c in cases) for (task in listOf("type", "category")) {
            val want = c[task] as Map<*, *>
            val x = Decide.build(spec, task, c["sender"] as String, c["body"] as String, tok)
            val ids = ints(want["ids"])
            assertEquals((want["bucket"] as Number).toInt(), x.bucket, "bucket $task ${c["sender"]}")
            assertEquals(ids, x.ids.take(ids.size), "ids $task ${c["sender"]}")
            assertTrue(x.ids.drop(ids.size).all { it == spec.pad })
            assertEquals(ids.size, x.mask.sum())
            assertTrue(x.mask.take(ids.size).all { it == 1 })
        }
    }

    @Test
    fun `both buckets and truncation are exercised`() {
        val buckets = cases.map { ((it["type"] as Map<*, *>)["bucket"] as Number).toInt() }
        assertTrue(256 in buckets && 384 in buckets)
        assertTrue(cases.any { ((it["type"] as Map<*, *>)["ids"] as List<*>).size == 384 && (it["body"] as String).length > 1500 })
    }

    @Test
    fun `argmax of the model logits maps to the reference label`() {
        var checked = 0
        for (c in cases) for (task in listOf("type", "category")) {
            val want = c[task] as Map<*, *>
            val (label, p) = Decide.top(spec, task, floats(want["logits"]))
            assertEquals(want["tflite"], label)
            assertTrue(p > 0f)
            (want["ref"] as? String)?.let {
                assertEquals(it, label, "reference label for $task ${c["sender"]}")
                checked++
            }
        }
        assertEquals(24, checked)
    }

    @Test
    fun `base calibration loads and its never sure bars hold`() {
        val cal = Calibration.fromJson(String(res("calibration.json")))
        assertTrue(cal.fits(Calibration.TYPE, spec.labels(Calibration.TYPE)))
        assertTrue(cal.fits(Calibration.CATEGORY, spec.labels(Calibration.CATEGORY)))
        assertTrue(cal.bar(Calibration.TYPE, "promo") > 1f)
        assertFalse(cal.sure(Calibration.TYPE, "promo", 1f))
    }
}
