package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals

class WeightsTest {
    private val a = "a1b2c3d4" + "0".repeat(56)
    private val b = "ffffffff" + "0".repeat(56)

    @Test
    fun `cache file names carry the task and the first eight hash characters`() {
        assertEquals("bert-type-a1b2c3d4.xnn", Weights.bert("type", a))
        assertEquals("bert-category-ffffffff.xnn", Weights.bert("category", b))
        assertEquals("decide-a1b2c3d4.xnn", Weights.decide(a))
    }

    @Test
    fun `only cache files that are not kept are stale`() {
        val keep = setOf(Weights.bert("type", a), Weights.bert("category", b))
        val files = listOf(Weights.bert("type", a), Weights.bert("type", b), Weights.bert("category", b), Weights.decide(a), "custom.json", "x.xnn.part", "models")
        assertEquals(listOf(Weights.bert("type", b), Weights.decide(a)), Weights.stale(files, keep))
        assertEquals(emptyList(), Weights.stale(files.filter { it.endsWith(".xnn") }, files.toSet()))
        assertEquals(listOf(Weights.bert("type", a)), Weights.stale(listOf(Weights.bert("type", a)), emptySet()))
    }
}
