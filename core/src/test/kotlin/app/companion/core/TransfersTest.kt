package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals

class TransfersTest {
    private val m = 60_000L

    @Test
    fun pairsTheNearestOppositeLegOnce() {
        val p = Transfers.pairs(listOf(Leg(1, true, 2_000_000, 0), Leg(2, false, 2_000_000, 9 * m), Leg(3, false, 2_000_000, m), Leg(4, true, 2_000_000, 2 * m)))
        assertEquals(listOf(1L to 3L, 4L to 2L), p.map { it.first.id to it.second.id })
    }

    @Test
    fun skipsOtherAmountsAndLateCredits() {
        assertEquals(0, Transfers.pairs(listOf(Leg(1, true, 2_000_000, 0), Leg(2, false, 1_999_900, m), Leg(3, false, 2_000_000, 30 * m))).size)
    }
}
