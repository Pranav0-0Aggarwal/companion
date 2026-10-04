package app.companion.core

import java.time.Instant
import java.time.ZoneId

object Repeat {
    val rules = listOf("daily", "weekly", "monthly")

    fun next(at: Long, rule: String?, zone: ZoneId): Long? {
        val t = Instant.ofEpochMilli(at).atZone(zone)
        return when (rule) {
            "daily" -> t.plusDays(1)
            "weekly" -> t.plusWeeks(1)
            "monthly" -> t.plusMonths(1)
            else -> return null
        }.toInstant().toEpochMilli()
    }

    fun after(at: Long, rule: String?, now: Long, zone: ZoneId): Long? {
        var n = next(at, rule, zone) ?: return null
        while (n <= now) n = next(n, rule, zone) ?: return null
        return n
    }
}
