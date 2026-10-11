package app.companion.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShotFilterTest {
    @Test
    fun `screenshot filter`() {
        assertTrue(ShotFilter.isShot("DCIM/Screenshots/", "a.png"))
        assertTrue(ShotFilter.isShot("Pictures/screenshots/", "x.jpg"))
        assertTrue(ShotFilter.isShot(null, "Screenshot_20261011.png"))
        assertFalse(ShotFilter.isShot("DCIM/Camera/", "IMG_1.jpg"))
    }
}
