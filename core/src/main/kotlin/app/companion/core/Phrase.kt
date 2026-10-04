package app.companion.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class Phrase(private val now: Long, private val zone: ZoneId) {
    private val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val words = mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "ten" to 10)
    private val count = "(\\d{1,3}|one|two|three|four|five|six|seven|ten)"

    private fun has(s: String, re: String) = Regex(re).containsMatchIn(s)

    private fun n(s: String) = s.toIntOrNull() ?: words.getValue(s)

    private fun month(ym: YearMonth) = ym.atDay(1) to ym.atEndOfMonth()

    private fun quarter(y: Int, q: Int) = LocalDate.of(y, q * 3 - 2, 1).let { it to YearMonth.from(it.plusMonths(2)).atEndOfMonth() }

    private fun back(d: LocalDate, future: Boolean) = if (!future && d.year > today.year) d.minusYears(1) else d

    fun slot(p: String) = Slots.find(p, now, zone)

    fun period(p: String, future: Boolean = false): Pair<LocalDate, LocalDate>? {
        val s = p.lowercase().trim()
        val iso = Regex("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b").findAll(s).mapNotNull { runCatching { LocalDate.parse(it.value) }.getOrNull() }.toList()
        if (iso.isNotEmpty()) return iso.first() to iso.last()
        Regex("\\s+to\\s+").split(s).takeIf { it.size == 2 }?.let { (a, b) ->
            val x = Dates.first(a, today)?.let { back(it, future) }
            val y = Dates.first(b, today)?.let { back(it, future) }
            if (x != null && y != null) return x to y
        }
        Dates.first(s, today)?.let { back(it, future) }?.let { return it to it }
        if (has(s, "day before yesterday|\\b(two|2) days ago|\\bparso\\b")) return today.minusDays(2).let { it to it }
        if (!future && has(s, "\\b(yesterday|kal)\\b")) return today.minusDays(1).let { it to it }
        if (has(s, "\\b(today|aaj)\\b")) return today to today
        Regex("(?:next|agle|in the next)\\s+$count\\s+(?:days?|din)").find(s)?.let { return today to today.plusDays(n(it.groupValues[1]).toLong()) }
        if (has(s, "next week|agle hafte")) return today.with(DayOfWeek.MONDAY).plusDays(7).let { it to it.plusDays(6) }
        if (future && has(s, "next month|agle mahine")) return month(YearMonth.from(today).plusMonths(1))
        Regex("(?:past|last|pichle|pichhle)\\s+$count\\s+(days?|din|weeks?|hafte|months?|mahine)\\b").find(s)?.let { m ->
            val k = n(m.groupValues[1])
            val u = m.groupValues[2]
            return when {
                u.startsWith("d") -> today.minusDays(k - 1L) to today
                u.startsWith("w") || u == "hafte" -> today.minusDays(k * 7L - 1) to today
                else -> today.minusMonths(k.toLong()).plusDays(1) to today
            }
        }
        if (has(s, "last quarter|pichli quarter")) {
            val q = (today.monthValue - 1) / 3 + 1
            return if (q == 1) quarter(today.year - 1, 4) else quarter(today.year, q - 1)
        }
        Regex("\\bq([1-4])\\b").find(s)?.let { return quarter(today.year, it.groupValues[1].toInt()) }
        if (has(s, "last week|pichle (hafte|week)|pichhle hafte")) return today.with(DayOfWeek.MONDAY).minusWeeks(1).let { it to it.plusDays(6) }
        if (has(s, "this week|is (hafte|week)")) return if (future) today to today.with(DayOfWeek.SUNDAY) else today.with(DayOfWeek.MONDAY) to today
        if (has(s, "last month|pichle mahine|pichhle mahine")) return month(YearMonth.from(today).minusMonths(1))
        if (has(s, "this month|is (mahine|month)")) return if (future) today to YearMonth.from(today).atEndOfMonth() else today.withDayOfMonth(1) to today
        if (has(s, "last year|pichle (saal|sal)")) return LocalDate.of(today.year - 1, 1, 1) to LocalDate.of(today.year - 1, 12, 31)
        if (has(s, "this year|is (saal|sal)")) return today.withDayOfYear(1) to today
        val w = s.split(Regex("[^a-z0-9]+"))
        for (i in w.indices) {
            val mo = months.indexOf(w[i].take(3)) + 1
            if (mo == 0 || w[i].length < 3 || !Regex("(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*").matches(w[i])) continue
            val year = w.getOrNull(i + 1)?.takeIf { Regex("20\\d\\d").matches(it) }?.toInt()
            return month(YearMonth.of(year ?: if (mo > today.monthValue && !future) today.year - 1 else today.year, mo))
        }
        return null
    }
}
