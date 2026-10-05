package app.companion.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object Expiry {
    private val marks = listOf(30, 7, 1, 0)

    private fun left(expires: LocalDate, today: LocalDate) = ChronoUnit.DAYS.between(today, expires)

    fun due(expires: LocalDate, today: LocalDate, sent: Set<Int>): Int? {
        val d = left(expires, today)
        val m = if (d <= 0) 0 else marks.lastOrNull { d <= it } ?: return null
        return m.takeIf { it !in sent }
    }

    fun next(expires: LocalDate, today: LocalDate, sent: Set<Int>): LocalDate? {
        if (due(expires, today, sent) != null) return today
        return marks.filter { it !in sent }.map { expires.minusDays(it.toLong()) }.filter { it > today }.minOrNull()
    }

    fun text(kind: DocKind, title: String, days: Int): String {
        val what = if (title.isBlank() || title == kind.label) kind.label else "${kind.label} ($title)"
        return when {
            days < 0 -> "$what has expired"
            days == 0 -> "$what expires today"
            days == 1 -> "$what expires tomorrow"
            else -> "$what expires in $days days"
        }
    }

    fun mask(sent: Set<Int>): String = sent.sorted().joinToString(",")

    fun parseSent(s: String): Set<Int> = s.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in marks }.toSet()
}
