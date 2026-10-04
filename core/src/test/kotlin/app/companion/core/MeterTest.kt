package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals

class MeterTest {
    @Test
    fun `speed is bytes over the recent window`() {
        val r = Rate(5000)
        assertEquals(0, r.bps())
        r.add(0, 0)
        r.add(1000, 2_000_000)
        assertEquals(2_000_000, r.bps())
        r.add(10_000, 2_000_000)
        r.add(11_000, 3_000_000)
        assertEquals(1_000_000, r.bps())
    }

    @Test
    fun `eta rounds up and is unknown at zero speed`() {
        val r = Rate()
        r.add(0, 0)
        assertEquals(-1, r.eta(100))
        r.add(1000, 1_000_000)
        assertEquals(61, r.eta(60_500_000))
    }

    @Test
    fun `formatting`() {
        assertEquals("556", Show.mb(556_466_892))
        assertEquals("557", Show.mbUp(556_466_892))
        assertEquals("3.2 MB/s", Show.speed(3_200_000))
        assertEquals("640 KB/s", Show.speed(640_000))
        assertEquals("estimating time", Show.eta(-1))
        assertEquals("45 s left", Show.eta(45))
        assertEquals("2 min left", Show.eta(61))
        assertEquals("1 h 1 min left", Show.eta(3660))
        assertEquals("2 h 0 min left", Show.eta(7199))
        assertEquals(0, Show.pct(5, 0))
    }
}
