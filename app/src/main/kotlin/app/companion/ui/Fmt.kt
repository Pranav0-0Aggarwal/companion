package app.companion.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private fun group(n: Long): String {
    val s = n.toString()
    if (s.length <= 3) return s
    val head = s.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
    return "$head,${s.takeLast(3)}"
}

fun plain(paise: Long): String {
    val a = kotlin.math.abs(paise)
    return "${group(a / 100)}.${(a % 100).toString().padStart(2, '0')}"
}

fun inr(paise: Long): String {
    val a = kotlin.math.abs(paise)
    return if (a % 100 == 0L) "₹${group(a / 100)}" else "₹${plain(a)}"
}

fun money(paise: Long, currency: String) = if (currency == "INR") inr(paise) else "$currency ${plain(paise)}"

private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM")
private val shortFmt = DateTimeFormatter.ofPattern("d MMM")
private val clockFmt = DateTimeFormatter.ofPattern("HH:mm")
private val monthFmt = DateTimeFormatter.ofPattern("MMMM")

fun zone(): ZoneId = ZoneId.systemDefault()

fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone()).toLocalDate()

fun today(): LocalDate = LocalDate.now(zone())

fun dayLabel(d: LocalDate): String = dayFmt.format(d)

fun shortDay(d: LocalDate): String = shortFmt.format(d)

fun clock(ms: Long): String = clockFmt.format(Instant.ofEpochMilli(ms).atZone(zone()))

fun monthName(d: LocalDate): String = monthFmt.format(d)

fun daysTo(d: LocalDate, from: LocalDate = today()): Int = ChronoUnit.DAYS.between(from, d).toInt()

fun inDays(n: Int): String = when {
    n < 0 -> "overdue ${-n} day${if (n == -1) "" else "s"}"
    n == 0 -> "today"
    n == 1 -> "tomorrow"
    else -> "in $n days"
}

fun codeText(code: String) = if (code.length == 6) "${code.take(3)} ${code.drop(3)}" else code

fun left(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
