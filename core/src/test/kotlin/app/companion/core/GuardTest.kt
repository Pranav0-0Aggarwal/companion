package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
            listOf("Phone is warm", "Battery saver on", "Battery below 20%", "Waiting for charger and idle", "Resuming…"),
            listOf(Guard.WARM, Guard.SAVER, Guard.LOW, Guard.CHARGER, Guard.RESUMING),
        )
    }

    @Test
    fun `a restored run says what it is waiting for`() {
        assertEquals(Guard.RESUMING, Guard.waiting(true))
        assertEquals(Guard.CHARGER, Guard.waiting(false))
    }

    @Test
    fun `a fresh run on battery needs nothing`() {
        assertTrue(Guard.wait(null, true).free)
        assertEquals(Wait(true, true, false, 0L), Guard.wait(null, false))
    }

    @Test
    fun `a paused run waits for what the guard saw`() {
        assertEquals(Wait(false, false, true, Guard.RECHECK), Guard.wait(Guard.LOW, true))
        assertEquals(Wait(true, false, false, Guard.RECHECK), Guard.wait(Guard.SAVER, true))
        assertEquals(Wait(false, false, false, Guard.RECHECK), Guard.wait(Guard.WARM, true))
    }

    @Test
    fun `a charger only run keeps charging and idle through every pause`() {
        listOf(Guard.WARM, Guard.SAVER, Guard.LOW).forEach {
            val w = Guard.wait(it, false)
            assertTrue(w.charging && w.idle, it)
            assertEquals(Guard.RECHECK, w.delay, it)
        }
    }

    @Test
    fun `every pause rechecks later so a satisfied constraint cannot spin`() {
        listOf(Guard.WARM, Guard.SAVER, Guard.LOW).forEach {
            assertFalse(Guard.wait(it, true).free, it)
            assertEquals(15 * 60_000L, Guard.wait(it, true).delay, it)
        }
    }

    @Test
    fun `a charge only hold waits for the charger after heat and saver`() {
        assertEquals(Guard.CHARGER, Guard.hold(v(charging = false), true))
        assertEquals(Guard.CHARGER, Guard.hold(v(charging = false, pct = 5), true))
        assertNull(Guard.hold(v(charging = true), true))
        assertNull(Guard.hold(v(charging = false), false))
        assertEquals(Guard.WARM, Guard.hold(v(thermal = 2, charging = false), true))
        assertEquals(Guard.SAVER, Guard.hold(v(saver = true, charging = false), true))
        assertEquals(Guard.WARM, Guard.hold(v(thermal = 4), true))
        assertEquals(Guard.WARM, Guard.hold(v(thermal = 2), true))
        assertNull(Guard.hold(v(thermal = 1), true))
    }

    @Test
    fun `two threads on battery and four on the charger`() {
        assertEquals(2, Guard.threads(v(charging = false)))
        assertEquals(4, Guard.threads(v(charging = true)))
    }

    @Test
    fun `a batch scores whole only when charging and cool else a live burst at most`() {
        assertEquals(40, Guard.take(v(), 40, true))
        assertEquals(Guard.LIVE, Guard.take(v(charging = false), 40, true))
        assertEquals(Guard.LIVE, Guard.take(v(thermal = 2), 40, true))
        assertEquals(Guard.LIVE, Guard.take(v(saver = true), 40, true))
        assertEquals(40, Guard.take(v(charging = false), 40, false))
        assertEquals(1, Guard.take(v(charging = false), 1, true))
        assertEquals(0, Guard.take(v(charging = false), 0, true))
    }
}
