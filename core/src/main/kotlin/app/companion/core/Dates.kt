package app.companion.core

import java.time.LocalDate

internal object Dates {
    private const val MON = "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val num = Regex("(?<![0-9])(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4}|\\d{2})(?![0-9])")
    private val short = Regex("(?<![0-9/.:])(\\d{1,2})/(\\d{1,2})(?![0-9/:])")
    private val dm = Regex("(?i)(?<![0-9])(\\d{1,2})(?:st|nd|rd|th)?[\\s-]*$MON\\b(?:[\\s,'-]*(\\d{4}|\\d{2})(?![0-9:]))?")
    private val md = Regex("(?i)\\b$MON\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b(?:,?\\s+(\\d{4})(?![0-9]))?")
    private val label = Regex("(?i)(?:due\\s+date|due\\s+on|due\\s+by|due\\s+before|payable\\s+(?:by|on)|pay\\s+(?:by|before)|last\\s+date)[^0-9a-z]{0,10}")
    private val due = Regex("(?i)\\bdue\\b")

    private fun month(s: String) = months.indexOf(s.lowercase().take(3)) + 1

    private fun year(y: String, m: Int, d: Int, ref: LocalDate): Int? {
        if (y.isNotEmpty()) return y.toInt().let { if (it < 100) 2000 + it else it }
        val c = runCatching { LocalDate.of(ref.year, m, d) }.getOrNull() ?: return null
        return if (c < ref.minusDays(30)) ref.year + 1 else ref.year
    }

    private fun build(y: String, m: Int, d: Int, ref: LocalDate): LocalDate? {
        val yy = year(y, m, d, ref) ?: return null
        return runCatching { LocalDate.of(yy, m, d) }.getOrNull()
    }

    fun find(t: String, from: Int, ref: LocalDate): Pair<Int, LocalDate>? {
        val hits = buildList {
            num.find(t, from)?.let { m -> build(m.groupValues[3], m.groupValues[2].toInt(), m.groupValues[1].toInt(), ref)?.let { add(m.range.first to it) } }
            short.find(t, from)?.let { m -> build("", m.groupValues[2].toInt(), m.groupValues[1].toInt(), ref)?.let { add(m.range.first to it) } }
            dm.find(t, from)?.let { m -> build(m.groupValues[3], month(m.groupValues[2]), m.groupValues[1].toInt(), ref)?.let { add(m.range.first to it) } }
            md.find(t, from)?.let { m -> build(m.groupValues[3], month(m.groupValues[1]), m.groupValues[2].toInt(), ref)?.let { add(m.range.first to it) } }
        }
        return hits.minByOrNull { it.first }
    }

    fun first(t: String, ref: LocalDate): LocalDate? = find(t, 0, ref)?.second

    fun due(t: String, ref: LocalDate): LocalDate? {
        label.find(t)?.let { m ->
            find(t, m.range.last + 1, ref)?.takeIf { it.first - m.range.last <= 3 }?.let { return it.second }
        }
        val d = due.find(t) ?: return null
        return find(t, d.range.last + 1, ref)?.takeIf { it.first - d.range.last <= 70 }?.second
    }
}
