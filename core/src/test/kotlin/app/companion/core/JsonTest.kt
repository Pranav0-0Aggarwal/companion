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

    @Test
    fun `write round trips`() {
        val m = mapOf("a" to "q\"x\\y\nz\u0001", "n" to null, "f" to 0.5f, "l" to 3L, "b" to true, "xs" to listOf("p", 1))
        assertEquals(mapOf("a" to "q\"x\\y\nz\u0001", "n" to null, "f" to 0.5, "l" to 3L, "b" to true, "xs" to listOf("p", 1L)), Json.obj(Json.write(m)))
        assertEquals("""{"f":null}""", Json.write(mapOf("f" to Float.NaN)))
    }
}
