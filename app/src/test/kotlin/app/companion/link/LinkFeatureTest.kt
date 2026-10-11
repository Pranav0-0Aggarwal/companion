package app.companion.link

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class LinkFeatureTest {
    private class Probe(override val key: String, override val cmds: Set<String>) : LinkFeature

    @Test
    fun `commands route to the feature that owns them`() {
        val a = Probe("a", setOf("x", "y"))
        val b = Probe("b", setOf("z"))
        LinkFeatures.all += listOf(a, b)
        try {
            assertSame(a, LinkFeatures.of("y"))
            assertSame(b, LinkFeatures.of("z"))
            assertNull(LinkFeatures.of("nope"))
        } finally {
            LinkFeatures.all -= setOf(a, b)
        }
        assertEquals(0, LinkFeatures.all.size)
    }
}
