package app.companion.core

import java.time.LocalDate
import java.time.YearMonth

data class Span(val from: LocalDate, val to: LocalDate)

object Cycle {
    private fun at(m: YearMonth, day: Int): LocalDate = m.atDay(day.coerceIn(1, m.lengthOfMonth()))

    fun span(stmtDay: Int, today: LocalDate): Span {
        val here = at(YearMonth.from(today), stmtDay)
        val start = if (here <= today) here else at(YearMonth.from(today).minusMonths(1), stmtDay)
        val next = at(YearMonth.from(start).plusMonths(1), stmtDay)
        return Span(start, next.minusDays(1))
    }

    private fun dueAfter(stmt: LocalDate, dueDay: Int): LocalDate {
        val same = at(YearMonth.from(stmt), dueDay)
        return if (same > stmt) same else at(YearMonth.from(stmt).plusMonths(1), dueDay)
    }

    fun due(stmtDay: Int, dueDay: Int, today: LocalDate): LocalDate {
        val s = span(stmtDay, today)
        val cur = dueAfter(s.from, dueDay)
        return if (cur >= today) cur else dueAfter(s.to.plusDays(1), dueDay)
    }

    fun payday(day: Int, today: LocalDate): LocalDate {
        val here = at(YearMonth.from(today), day)
        return if (here >= today) here else at(YearMonth.from(today).plusMonths(1), day)
    }
}
