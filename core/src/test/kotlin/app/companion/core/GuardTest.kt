package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GuardTest {
    private fun v(thermal: Int = 0, saver: Boolean = false, charging: Boolean = true, pct: Int = 80) = Vitals(thermal, saver, charging, pct)

    @Test
    fun `a cool phone with a healthy battery runs`() {
        assertNull(Guard.hold(v()))
        assertNull(Guard.hold(v(charging = false, pct = 80)))
    }

    @Test
    fun `thermal status is the decision table`() {
        val want = listOf(null, null, Guard.WARM, Guard.WARM, Guard.WARM, Guard.WARM, Guard.WARM)
        want.forEachIndexed { s, w -> assertEquals(w, Guard.hold(v(thermal = s)), "thermal $s") }
    }

    @Test
    fun `battery saver pauses even on the charger`() {
        assertEquals(Guard.SAVER, Guard.hold(v(saver = true)))
        assertEquals(Guard.SAVER, Guard.hold(v(saver = true, charging = false)))
    }

    @Test
    fun `below twenty percent pauses only on battery`() {
        assertEquals(Guard.LOW, Guard.hold(v(charging = false, pct = 19)))
        assertEquals(Guard.LOW, Guard.hold(v(charging = false, pct = 0)))
        assertNull(Guard.hold(v(charging = false, pct = 20)))
        assertNull(Guard.hold(v(charging = true, pct = 5)))
    }

    @Test
    fun `heat wins over saver and saver wins over a low battery`() {
        assertEquals(Guard.WARM, Guard.hold(v(thermal = 3, saver = true, charging = false, pct = 5)))
        assertEquals(Guard.SAVER, Guard.hold(v(saver = true, charging = false, pct = 5)))
    }

    @Test
    fun `a light status naps between batches and nothing else does`() {
        assertEquals(Guard.NAP, Guard.nap(v(thermal = Guard.LIGHT)))
        listOf(0, 2, 3, 6).forEach { assertEquals(0L, Guard.nap(v(thermal = it))) }
    }

    @Test
    fun `reasons are the strings the screen shows`() {
        assertEquals(
            listOf("Phone is warm", "Battery saver on", "Battery below 20%", "Waiting for charger"),
            listOf(Guard.WARM, Guard.SAVER, Guard.LOW, Guard.CHARGER),
        )
    }
}
