package app.companion.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class Slot(val date: LocalDate, val time: LocalTime?) {
    fun millis(zone: ZoneId, default: LocalTime = LocalTime.of(9, 0)): Long =
        LocalDateTime.of(date, time ?: default).atZone(zone).toInstant().toEpochMilli()
}

object Slots {
    private val clock = Regex("(?i)(?<![0-9:])(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)")
    private val colon = Regex("(?<![0-9:])([01]?\\d|2[0-3]):([0-5]\\d)(?![0-9:])")
    private val bare = Regex("(?i)(?:\\bat|@)\\s*(\\d{1,2})(?![0-9:.]|\\s*(?:am|pm|%|rs|inr|₹))")
    private val within = Regex("(?i)\\bin\\s+(\\d{1,3})\\s*(min|minute|minutes|mins|hour|hours|hr|hrs|h)\\b")
    private val evening = Regex("(?i)dinner|tonight|evening|night|party|drinks")
    private val days = mapOf(
        "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY, "thu" to DayOfWeek.THURSDAY,
        "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY, "sun" to DayOfWeek.SUNDAY,
    )
    private val weekday = Regex("(?i)\\b(next\\s+)?(mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun)(?:day|nesday|sday|rsday|urday|sday)?\\b")

    private fun time(t: String): LocalTime? {
        clock.find(t)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].ifEmpty { "0" }.toInt()
            if (h !in 1..12) return null
            val pm = m.groupValues[3].lowercase().startsWith("p")
            return LocalTime.of(h % 12 + if (pm) 12 else 0, min)
        }
        colon.find(t)?.let { return LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
        bare.find(t)?.let { m ->
            val h = m.groupValues[1].toInt()
            if (h !in 1..12) return null
            val pm = h in 1..6 || (h in 7..11 && evening.containsMatchIn(t))
            return LocalTime.of(if (h == 12) 12 else if (pm) h + 12 else h, 0)
        }
        if (Regex("(?i)tonight").containsMatchIn(t)) return LocalTime.of(20, 0)
        return null
    }

    fun find(text: String, now: Long, zone: ZoneId): Slot? {
        val at = Instant.ofEpochMilli(now).atZone(zone).toLocalDateTime()
        val today = at.toLocalDate()
        within.find(text)?.let { m ->
            val n = m.groupValues[1].toLong()
            val d = if (m.groupValues[2].lowercase().startsWith("m")) at.plusMinutes(n) else at.plusHours(n)
            return Slot(d.toLocalDate(), d.toLocalTime().withSecond(0).withNano(0))
        }
        val t = time(text)
        val low = text.lowercase()
        val date: LocalDate? = when {
            Regex("day after tomorrow").containsMatchIn(low) -> today.plusDays(2)
            Regex("\\b(tomorrow|tmrw|tmw)\\b").containsMatchIn(low) -> today.plusDays(1)
            Regex("\\b(today|tonight)\\b").containsMatchIn(low) -> today
            else -> weekday.find(text)?.let { m ->
                val dow = days.getValue(m.groupValues[2].lowercase().take(3))
                var d = today.plusDays(((dow.value - today.dayOfWeek.value + 7) % 7).toLong())
                if (m.groupValues[1].isNotEmpty() && d == today) d = d.plusDays(7)
                if (d == today && t != null && t <= at.toLocalTime()) d = d.plusDays(7)
                d
            } ?: Dates.first(text, today)
        }
        if (date != null) return Slot(date, t)
        if (t != null) return Slot(if (t > at.toLocalTime()) today else today.plusDays(1), t)
        return null
    }
}
