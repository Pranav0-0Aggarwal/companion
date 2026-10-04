package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TwinTest {
    private val t = 1_790_000_000_000L
    private val s = Twin.SLACK

    private fun same(at: Long, sent: Long, date: Long, other: String = "VM-HDFCBK") = Twin.same("VM-HDFCBK", at, other, sent, date)

    @Test
    fun `a live link at the sent time matches`() {
        assertTrue(same(t, t, t + 4_000))
    }

    @Test
    fun `a link at the received time matches`() {
        assertTrue(same(t + 4_000, t, t + 4_000))
    }

    @Test
    fun `two seconds is inside and just past is outside`() {
        assertTrue(same(t + s, t, t))
        assertTrue(same(t - s, t, t))
        assertFalse(same(t + s + 1, t, t))
        assertFalse(same(t - s - 1, t, t))
    }

    @Test
    fun `either clock can carry the match`() {
        val sent = t
        val date = t + 60_000
        assertTrue(same(sent + 1_500, sent, date))
        assertTrue(same(date - 1_500, sent, date))
        assertFalse(same(t + 30_000, sent, date))
    }

    @Test
    fun `an unset sent time falls back to the received time`() {
        assertEquals(listOf(t, t), Twin.times(0, t))
        assertEquals(listOf(t, t), Twin.times(-1, t))
        assertTrue(same(t + 1_000, 0, t))
        assertFalse(same(1_000, 0, t))
    }

    @Test
    fun `another sender never matches`() {
        assertFalse(same(t, t, t, other = "AX-ICICIB"))
        assertFalse(same(t, t, t, other = "vm-hdfcbk"))
    }

    @Test
    fun `spans cover both clocks`() {
        assertEquals(listOf((t - s)..(t + s), (t + 5_000 - s)..(t + 5_000 + s)), Twin.spans(t, t + 5_000))
    }
}
