package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals

class JsonTest {
    @Test
    fun `nested values`() {
        val m = Json.obj("""{"a": [1, 2.5, -3], "b": {"c": "x\nyé", "d": null, "e": true}, "f": []}""")
        assertEquals(listOf(1L, 2.5, -3L), m["a"])
        assertEquals(mapOf("c" to "x\nyé", "d" to null, "e" to true), m["b"])
        assertEquals(emptyList<Any?>(), m["f"])
    }
}
