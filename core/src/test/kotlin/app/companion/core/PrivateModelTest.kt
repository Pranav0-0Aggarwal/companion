package app.companion.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue

class PrivateModelTest {
    private val dir = System.getenv("COMPANION_PRIVATE_DIR")?.let(::File)?.takeIf { File(it, "model_spec.json").isFile && File(it, "tokenizer.json").isFile }

    private fun ints(v: Any?) = (v as List<*>).map { (it as Number).toInt() }

    private val vectors = (Json.obj(String(ClassLoader.getSystemResourceAsStream("bert/vectors_v2.json")!!.readBytes(), Charsets.UTF_8))["vectors"] as List<*>).map { it as Map<*, *> }

    @Test
    fun `the private spec calibration and tokenizer match the synthetic vectors`() {
        assumeTrue(dir != null)
        val d = dir!!
        val spec = BertSpec.fromJson(File(d, "model_spec.json").readText())
        val bpe = Bpe.fromJson(File(d, spec.tokenizer).readText())
        val cal = Calibration.fromJson(File(d, spec.calibration).readText())
        assertTrue(cal.fits("type", spec.labels("type")))
        assertTrue(cal.fits("category", spec.labels("category")))
        assertEquals(spec.model, cal.model)
        assertEquals(listOf(64, 96, 128), spec.buckets)
        for (t in listOf("type", "category")) for (b in spec.buckets) assertTrue(assertNotNull(spec.signature(t, b)).named, "$t $b")
        for (v in vectors) {
            val want = ints(v["input_ids"])
            val x = Bert.build(spec, bpe, v["sender"] as String, v["text"] as String)
            assertEquals(want, x.ids.take(want.size), "ids for ${v["input"]}")
            assertEquals(spec.buckets.first { it >= want.size }, x.bucket)
            assertEquals(want.size, x.mask.sum())
            assertTrue(x.ids.drop(want.size).all { it == spec.pad })
        }
    }
}
