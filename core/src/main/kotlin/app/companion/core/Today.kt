package app.companion.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object Safe {
    fun owed(dues: List<Pair<LocalDate?, Long>>, payday: LocalDate) = dues.filter { (d, _) -> d != null && d < payday }.sumOf { it.second }

    fun days(today: LocalDate, payday: LocalDate) = ChronoUnit.DAYS.between(today, payday).toInt().coerceAtLeast(1)

    fun perDay(budget: Long?, spent: Long, owed: Long, days: Int?): Long? {
        if (budget == null || budget <= 0 || days == null) return null
        return ((budget - spent - owed) / days.coerceAtLeast(1) / 100 * 100).coerceAtLeast(0)
    }
}

object Strip {
    fun days(selected: LocalDate, n: Int = 7): List<LocalDate> = List(n) { selected.plusDays((it - n / 2).toLong()) }
}

object Timeline {
    fun <T> order(l: List<T>, at: (T) -> Long?, rank: (T) -> Int = { 0 }): List<T> =
        l.sortedWith(compareBy<T>({ at(it) != null }, { at(it) ?: 0L }, rank))

    fun <T> nowAt(sorted: List<T>, at: (T) -> Long?, now: Long): Int =
        sorted.indexOfFirst { at(it)?.let { t -> t > now } == true }.let { if (it < 0) sorted.size else it }

    fun <T> around(sorted: List<T>, at: (T) -> Long?, now: Long, n: Int): List<T> {
        val i = nowAt(sorted, at, now)
        val from = minOf(maxOf(i - 1, 0), maxOf(sorted.size - n, 0))
        return sorted.drop(from).take(n)
    }
}

object Privacy {
    fun line(read: Int) = "Read ${if (read == 0) "nothing" else "$read ${if (read == 1) "message" else "messages"}"} today · sent nothing"
}

object Spark {
    fun window(points: List<Pair<LocalDate, Double>>, today: LocalDate, days: Int = 28): List<Pair<LocalDate, Double>> =
        points.filter { it.first > today.minusDays(days.toLong()) && it.first <= today }.sortedBy { it.first }
}
