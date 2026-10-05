package app.companion.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

enum class Eat { Breakfast, Lunch, Snacks, Dinner }

data class Plate(val slot: Eat, val at: Long)

data class Quiet(val from: LocalTime, val to: LocalTime) {
    fun has(t: LocalTime) = if (from <= to) t >= from && t < to else t >= from || t < to

    fun clear(t: ZonedDateTime): ZonedDateTime {
        val lt = t.toLocalTime()
        if (!has(lt)) return t
        val d = if (from > to && lt >= from) t.toLocalDate().plusDays(1) else t.toLocalDate()
        return d.atTime(to).atZone(t.zone)
    }

    companion object {
        val default = Quiet(LocalTime.of(22, 30), LocalTime.of(7, 0))
    }
}

object MealTimes {
    val defaults: Map<Eat, LocalTime> = mapOf(
        Eat.Breakfast to LocalTime.of(9, 0),
        Eat.Lunch to LocalTime.of(13, 30),
        Eat.Snacks to LocalTime.of(17, 30),
        Eat.Dinner to LocalTime.of(21, 0),
    )
    private val bands = mapOf(
        Eat.Breakfast to 360..660,
        Eat.Lunch to 690..960,
        Eat.Snacks to 900..1170,
        Eat.Dinner to 1110..1380,
    )
    private const val DAY = 86_400_000L
    private const val DAYS = 21
    private const val MIN = 3

    fun learn(samples: List<Pair<Eat, Long>>, now: Long, zone: ZoneId): Map<Eat, LocalTime> {
        val by = samples.filter { it.second in now - DAYS * DAY..now }
            .groupBy({ it.first }, { Instant.ofEpochMilli(it.second).atZone(zone).let { z -> z.hour * 60 + z.minute } })
        return Eat.entries.associateWith { e ->
            val m = by[e].orEmpty().sorted()
            if (m.size < MIN) defaults.getValue(e)
            else {
                val mid = (m[(m.size - 1) / 2] + m[m.size / 2]) / 2
                mid.coerceIn(bands.getValue(e)).let { LocalTime.of(it / 60, it % 60) }
            }
        }
    }
}

object Nudge {
    private const val MIN = 60_000L
    private const val GRACE = 60 * MIN
    private const val WINDOW = 4 * 60 * MIN

    private fun usual(day: LocalDate, e: Eat, times: Map<Eat, LocalTime>, zone: ZoneId) =
        day.atTime(times[e] ?: MealTimes.defaults.getValue(e)).atZone(zone).toInstant().toEpochMilli()

    fun due(
        now: Long,
        zone: ZoneId,
        times: Map<Eat, LocalTime>,
        logged: Set<Eat>,
        foodPaidAt: List<Long>,
        nudged: Set<Eat>,
        on: Set<Eat>,
        quiet: Quiet = Quiet.default,
    ): Eat? {
        val t = Instant.ofEpochMilli(now).atZone(zone)
        if (quiet.has(t.toLocalTime())) return null
        val day = t.toLocalDate()
        return Eat.entries.filter { e ->
            val u = usual(day, e, times, zone)
            e in on && e !in logged && e !in nudged && now >= u + GRACE && now < u + GRACE + WINDOW && foodPaidAt.none { it in u - GRACE..now }
        }.minByOrNull { usual(day, it, times, zone) }
    }

    private fun at(e: Eat, day: LocalDate, times: Map<Eat, LocalTime>, zone: ZoneId, quiet: Quiet, from: Long): Long? {
        val s = usual(day, e, times, zone) + GRACE
        return quiet.clear(Instant.ofEpochMilli(maxOf(s, from)).atZone(zone)).toInstant().toEpochMilli().takeIf { it < s + WINDOW }
    }

    fun next(
        now: Long,
        zone: ZoneId,
        times: Map<Eat, LocalTime>,
        logged: Set<Eat>,
        foodPaidAt: List<Long>,
        nudged: Set<Eat>,
        on: Set<Eat>,
        quiet: Quiet = Quiet.default,
    ): Long? {
        val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val today = Eat.entries.filter { e ->
            e in on && e !in logged && e !in nudged && foodPaidAt.none { it in usual(day, e, times, zone) - GRACE..now }
        }.mapNotNull { at(it, day, times, zone, quiet, now) }
        return today.minOrNull() ?: Eat.entries.filter { it in on }.mapNotNull { at(it, day.plusDays(1), times, zone, quiet, Long.MIN_VALUE) }.minOrNull()
    }
}

object Spend {
    fun prompt(merchant: String?, category: String?, paise: Long, hasItems: Boolean, inBrandDb: Boolean): String? {
        if (category != Category.Food.label || hasItems || inBrandDb || paise <= 0 || merchant.isNullOrBlank()) return null
        return "Logged ${Rs.of(paise)} at ${merchant.trim()}. What did you have?"
    }
}

data class Trend(val slopePerWeek: Double, val avg7: Double, val latest: Double)

object WeightNudge {
    private const val RECENT = 2L
    private val num = Regex("(?i)\\s*(\\d{2,3}(?:[.,]\\d{1,2})?)\\s*(?:kgs?|kilos?)?\\s*\\.?\\s*")

    fun due(
        now: Long,
        zone: ZoneId,
        lastWeighIn: Long?,
        nudgedThisWeek: Boolean,
        at: LocalTime = LocalTime.of(9, 0),
        day: DayOfWeek = DayOfWeek.SUNDAY,
        quiet: Quiet = Quiet.default,
    ): Boolean {
        val t = Instant.ofEpochMilli(now).atZone(zone)
        if (nudgedThisWeek || t.dayOfWeek != day || t.toLocalTime() < at || quiet.has(t.toLocalTime())) return false
        return lastWeighIn == null || lastWeighIn < t.toLocalDate().minusDays(RECENT).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun next(now: Long, zone: ZoneId, at: LocalTime = LocalTime.of(9, 0), day: DayOfWeek = DayOfWeek.SUNDAY, quiet: Quiet = Quiet.default): Long {
        val d = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return (0L..7L).map { d.plusDays(it) }.filter { it.dayOfWeek == day }
            .map { quiet.clear(it.atTime(at).atZone(zone)).toInstant().toEpochMilli() }.first { it > now }
    }

    fun trend(points: List<Pair<LocalDate, Double>>): Trend? {
        val last = points.maxOfOrNull { it.first } ?: return null
        val p = points.filter { it.first > last.minusWeeks(8) }.sortedBy { it.first }
        if (p.size < 2) return null
        val x = p.map { ChronoUnit.DAYS.between(p[0].first, it.first).toDouble() }
        val y = p.map { it.second }
        val mx = x.average()
        val my = y.average()
        val sxx = x.sumOf { (it - mx) * (it - mx) }
        val slope = if (sxx == 0.0) 0.0 else x.indices.sumOf { (x[it] - mx) * (y[it] - my) } / sxx
        val avg = p.filter { it.first > last.minusDays(7) }.map { it.second }.average()
        return Trend(slope * 7, avg, p.last().second)
    }

    fun parseKg(text: String): Double? =
        num.matchEntire(text)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it in 25.0..300.0 }
}
