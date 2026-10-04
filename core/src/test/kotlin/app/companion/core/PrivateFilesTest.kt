package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrivateFilesTest {
    private val base = setOf("decide.tflite", "needle3.cact", "calibration.json")
    private val spec = """{"arch":"modernbert-classifier","buckets":[32],"tokenizer":"tokenizer.json","tasks":{"type":{"file":"type.tflite","labels":["a"]},"category":{"file":"category.tflite","labels":["b"]}}}"""
    private val a = "a".repeat(64)
    private val b = "b".repeat(64)

    private fun bad(l: List<Line>) = l.filter { !it.ok }.map { it.name }

    @Test
    fun plainNames() {
        assertEquals("type.tflite", PrivateFiles.plain("type.tflite"))
        listOf(null, "", "../x", "a/b", "a\\b", "..", "a..b", "x\u0000y", "x\ny", "a".repeat(129)).forEach { assertNull(PrivateFiles.plain(it), it) }
    }

    @Test
    fun traversalNamesRejected() {
        val ok = PrivateFiles.allowed(base, spec)
        val l = PrivateFiles.screen(listOf("../custom.json", "/etc/passwd", "a/../decide.tflite", "decide.tflite", "custom.json"), ok)
        assertEquals(listOf("../custom.json", "/etc/passwd", "a/../decide.tflite"), bad(l))
        assertFalse(PrivateFiles.commit(l))
    }

    @Test
    fun unknownNamesRejected() {
        val l = PrivateFiles.screen(listOf("custom.json", "evil.so", "type.tflite", "extra.tflite"), PrivateFiles.allowed(base, null))
        assertEquals(listOf("evil.so", "type.tflite", "extra.tflite"), bad(l))
    }

    @Test
    fun specNamesTaskFiles() {
        val l = PrivateFiles.screen(listOf("custom.json", "model_spec.json", "type.tflite", "category.tflite", "tokenizer.json", "decide.tflite"), PrivateFiles.allowed(base, spec))
        assertTrue(PrivateFiles.commit(l))
    }

    @Test
    fun brokenSpecAddsNothing() {
        assertEquals(base + "model_spec.json" + "custom.json", PrivateFiles.allowed(base, "{nope"))
    }

    @Test
    fun manifestIsRequiredAndNamesAreUnique() {
        assertEquals(listOf("custom.json"), bad(PrivateFiles.screen(listOf("decide.tflite"), PrivateFiles.allowed(base, null))))
        assertEquals(listOf("decide.tflite"), bad(PrivateFiles.screen(listOf("decide.tflite", "custom.json", "decide.tflite"), PrivateFiles.allowed(base, null))))
    }

    @Test
    fun manifestParsing() {
        assertEquals(mapOf("x" to a), PrivateFiles.manifest("""{"x":"${a.uppercase()}"}"""))
        listOf(null, "[]", "{", """{"x":"abc"}""", """{"x":1}""", """{"x":"${a}0"}""", """{"x":null}""").forEach { assertNull(PrivateFiles.manifest(it), it) }
    }

    @Test
    fun matchingFilesVerify() {
        val l = PrivateFiles.verify(mapOf("type.tflite" to a, "model_spec.json" to b), mapOf("type.tflite" to a))
        assertTrue(PrivateFiles.commit(l))
    }

    @Test
    fun oneMismatchAbortsEverything() {
        val l = PrivateFiles.verify(mapOf("type.tflite" to a, "category.tflite" to a, "tokenizer.json" to b), mapOf("type.tflite" to a, "category.tflite" to b, "tokenizer.json" to b))
        assertEquals(listOf("category.tflite"), bad(l))
        assertFalse(PrivateFiles.commit(l))
    }

    @Test
    fun unlistedFileAndBadManifestAbort() {
        assertEquals(listOf("type.tflite"), bad(PrivateFiles.verify(mapOf("type.tflite" to a), emptyMap())))
        val none = PrivateFiles.verify(mapOf("type.tflite" to a), null)
        assertEquals(listOf("custom.json", "type.tflite"), bad(none))
        assertFalse(PrivateFiles.commit(none))
    }

    @Test
    fun emptyDecisionDoesNotCommit() {
        assertFalse(PrivateFiles.commit(emptyList()))
    }

    @Test
    fun roomChecksCapAndFreeSpace() {
        val gb = 1L shl 30
        assertTrue(PrivateFiles.room(listOf("a", "b"), listOf(gb, -1), 2 * gb).isEmpty())
        assertEquals(listOf("big.gguf"), bad(PrivateFiles.room(listOf("big.gguf"), listOf(3 * gb), 10 * gb)))
        assertEquals(listOf("Storage"), bad(PrivateFiles.room(listOf("a"), listOf(gb), gb)))
    }
}
