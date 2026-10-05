package app.companion.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManifestTest {
    @Test
    fun `every spec is pinned and the classifier set comes from models-v4`() {
        assertTrue(Manifest.all.all(Models::pinned))
        assertEquals(setOf("model_spec.json", "tokenizer.json", "calibration.json", "type.tflite"), Manifest.bert.map { it.file }.toSet())
        assertTrue(Manifest.bert.all { it.tag == "models-v4" })
        assertEquals("https://github.com/Pranav0-0Aggarwal/companion/releases/download/models-v4/type.tflite", Manifest.url(Manifest.type))
        assertEquals(412_719_584L, Manifest.bert.sumOf { it.bytes })
        assertEquals(Manifest.all.size, Manifest.all.map { it.file }.toSet().size)
    }
}
