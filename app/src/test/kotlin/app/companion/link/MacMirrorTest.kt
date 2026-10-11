package app.companion.link

import app.companion.link.bulk.Bulk
import app.companion.link.feature.MacMirror
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MacMirrorTest {
    @Test
    fun `registers the mirror handler on its own share key and stops twice`() {
        val n = LinkFeatures.all.size
        MacMirror.register { error("unused") }
        try {
            assertNotNull(Bulk.handlers["mirror"])
            assertEquals("mirror", MacMirror.key)
            assertEquals(true, MacMirror.cmds.isEmpty())
            MacMirror.stop()
            MacMirror.stop()
        } finally {
            LinkFeatures.all -= MacMirror
            Bulk.handlers -= "mirror"
        }
        assertEquals(n, LinkFeatures.all.size)
        assertNull(Bulk.handlers["mirror"])
    }
}
