package app.companion.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhraseTest {
    private val p = Phrase(NOW, IST)

    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate.of(y, m, day)

    @Test
    fun `relative periods`() {
        assertEquals(d(9, 1) to d(9, 30), p.period("last month"))
        assertEquals(d(9, 28) to d(10, 4), p.period("this week"))
        assertEquals(d(9, 21) to d(9, 27), p.period("last week"))
        assertEquals(d(10, 3) to d(10, 3), p.period("yesterday"))
        assertEquals(d(10, 2) to d(10, 2), p.period("two days ago"))
        assertEquals(d(10, 4) to d(10, 4), p.period("aaj"))
        assertEquals(d(1, 1) to d(10, 4), p.period("this year"))
        assertEquals(d(1, 1, 2025) to d(12, 31, 2025), p.period("last year"))
    }

    @Test
    fun `counted periods include today`() {
        assertEquals(d(9, 28) to d(10, 4), p.period("last 7 days"))
        assertEquals(d(9, 21) to d(10, 4), p.period("past 2 weeks"))
        assertEquals(d(9, 5) to d(10, 4), p.period("last one month"))
    }

    @Test
    fun `hinglish periods`() {
        assertEquals(d(9, 1) to d(9, 30), p.period("pichle mahine"))
        assertEquals(d(9, 21) to d(9, 27), p.period("pichle hafte"))
        assertEquals(d(10, 3) to d(10, 3), p.period("kal"))
        assertEquals(d(9, 28) to d(10, 4), p.period("pichle 7 din"))
    }

    @Test
    fun `quarters`() {
        assertEquals(d(4, 1) to d(6, 30), p.period("Q2"))
        assertEquals(d(7, 1) to d(9, 30), p.period("last quarter"))
    }

    @Test
    fun `named months look back unless they are in the future`() {
        assertEquals(d(7, 1) to d(7, 31), p.period("July"))
        assertEquals(d(12, 1, 2025) to d(12, 31, 2025), p.period("december"))
        assertEquals(d(3, 1, 2024) to d(3, 31, 2024), p.period("March 2024"))
    }

    @Test
    fun `dates and ranges`() {
        assertEquals(d(9, 5) to d(9, 5), p.period("5 Sep"))
        assertEquals(d(9, 1) to d(9, 15), p.period("1 Sep to 15 Sep"))
        assertEquals(d(9, 1) to d(9, 15), p.period("2026-09-01 to 2026-09-15"))
    }

    @Test
    fun `bills look forward`() {
        assertEquals(d(10, 4) to d(10, 4).with(java.time.DayOfWeek.SUNDAY), p.period("this week", future = true))
        assertEquals(d(10, 4) to d(10, 31), p.period("this month", future = true))
        assertEquals(d(11, 1) to d(11, 30), p.period("next month", future = true))
        assertEquals(d(10, 4) to d(10, 14), p.period("next 10 days", future = true))
    }

    @Test
    fun `nonsense has no period`() {
        assertNull(p.period("blue elephants"))
        assertNull(p.period(""))
    }
}
