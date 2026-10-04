package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaceTest {
    @Test
    fun `there is no estimate before the first batch`() {
        assertNull(Pace().eta(100))
    }

    @Test
    fun `the first batch sets the rate and the estimate rounds up`() {
        val p = Pace()
        p.add(32, 4_000)
        assertEquals(13, p.eta(100))
        assertEquals(0, p.eta(0))
    }

    @Test
    fun `later batches are smoothed rather than replacing the rate`() {
        val p = Pace(0.5)
        p.add(10, 1_000)
        p.add(30, 1_000)
        assertEquals(5, p.eta(100))
    }

    @Test
    fun `empty or instant batches are ignored`() {
        val p = Pace()
        p.add(0, 1_000)
        p.add(10, 0)
        assertNull(p.eta(10))
        p.add(10, 1_000)
        p.add(0, 500)
        p.add(5, -1)
        assertEquals(10, p.eta(100))
    }
}
