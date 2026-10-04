package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CycleTest {
    @Test
    fun `span runs from statement day to the day before the next`() {
        assertEquals(Span(day(2026, 9, 12), day(2026, 10, 11)), Cycle.span(12, day(2026, 10, 4)))
        assertEquals(Span(day(2026, 10, 12), day(2026, 11, 11)), Cycle.span(12, day(2026, 10, 12)))
        assertEquals(Span(day(2026, 12, 12), day(2027, 1, 11)), Cycle.span(12, day(2026, 12, 31)))
    }

    @Test
    fun `short months clamp the day`() {
        assertEquals(Span(day(2026, 2, 28), day(2026, 3, 30)), Cycle.span(31, day(2026, 3, 5)))
    }

    @Test
    fun `due is the first due day after the statement`() {
        assertEquals(day(2026, 10, 6), Cycle.due(12, 6, day(2026, 10, 4)))
        assertEquals(day(2026, 11, 6), Cycle.due(12, 6, day(2026, 10, 7)))
        assertEquals(day(2026, 10, 28), Cycle.due(5, 28, day(2026, 10, 7)))
    }

    @Test
    fun `payday rolls to next month once passed`() {
        assertEquals(day(2026, 10, 25), Cycle.payday(25, day(2026, 10, 4)))
        assertEquals(day(2026, 11, 1), Cycle.payday(1, day(2026, 10, 4)))
        assertEquals(day(2026, 2, 28), Cycle.payday(31, day(2026, 2, 3)))
    }
}
