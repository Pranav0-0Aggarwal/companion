package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoverFitTest {
    @Test
    fun `a delivery code outranks everything and a fresh code outranks a meeting`() {
        assertEquals(Focus.Out, Now.focus(true, true, true, 0, 3))
        assertEquals(Focus.Code, Now.focus(false, true, true, 0, 3))
        assertEquals(Focus.Meet, Now.focus(false, false, true, 0, 3))
    }

    @Test
    fun `a bill counts only when it is due today or overdue`() {
        assertEquals(Focus.Due, Now.focus(false, false, false, 0, 3))
        assertEquals(Focus.Due, Now.focus(false, false, false, -2, 0))
        assertEquals(Focus.Spent, Now.focus(false, false, false, 1, 3))
        assertEquals(Focus.Spent, Now.focus(false, false, false, null, 0))
    }

    @Test
    fun `details need the app unlocked and the lock screen detail pref when the device is locked`() {
        assertTrue(Reveal.of(false, false, false, false))
        assertFalse(Reveal.of(true, false, false, true))
        assertTrue(Reveal.of(true, true, false, false))
        assertFalse(Reveal.of(false, false, true, false))
        assertTrue(Reveal.of(false, false, true, true))
        assertFalse(Reveal.of(true, false, true, true))
    }

    @Test
    fun `only a small window in both directions switches to the cover layout`() {
        assertTrue(Fit.cover(361, 399))
        assertTrue(Fit.cover(474, 523))
        assertFalse(Fit.cover(481, 399))
        assertFalse(Fit.cover(361, 601))
        assertFalse(Fit.cover(360, 840))
    }
}
