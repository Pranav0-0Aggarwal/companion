package app.companion.core

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RepeatTest {
    private fun ms(m: Int, d: Int) = LocalDateTime.of(2026, m, d, 9, 0).atZone(IST).toInstant().toEpochMilli()

    @Test
    fun `next occurrence`() {
        assertEquals(ms(10, 5), Repeat.next(ms(10, 4), "daily", IST))
        assertEquals(ms(10, 11), Repeat.next(ms(10, 4), "weekly", IST))
        assertEquals(ms(11, 4), Repeat.next(ms(10, 4), "monthly", IST))
        assertNull(Repeat.next(ms(10, 4), null, IST))
        assertNull(Repeat.next(ms(10, 4), "yearly", IST))
    }

    @Test
    fun `after skips past occurrences`() {
        assertEquals(ms(10, 7), Repeat.after(ms(10, 1), "daily", ms(10, 6) + 1, IST))
    }

    @Test
    fun `month end clamps`() {
        assertEquals(ms(2, 28), Repeat.next(ms(1, 31), "monthly", IST))
    }
}
