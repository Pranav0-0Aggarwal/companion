package app.companion.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

data class TSpend(val id: Long, val at: Long, val title: String, val category: String?, val paise: Long, val share: Double) {
    val cost get() = (paise * share).roundToLong()
}

data class DayGroup(val day: LocalDate, val rows: List<TSpend>) {
    val total get() = rows.sumOf { it.cost }
}

object TripView {
    fun byDay(items: List<TSpend>, zone: ZoneId): List<DayGroup> =
        items.groupBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }.toSortedMap()
            .map { (d, l) -> DayGroup(d, l.sortedBy { it.at }) }
}

object DocState {
    const val SOON = 30

    fun of(days: Int?): Due = when {
        days == null -> Due.None
        days < 0 -> Due.Expired
        days <= SOON -> Due.Soon
        else -> Due.Ok
    }

    fun chip(days: Int?, expires: LocalDate?): String? = when (of(days)) {
        Due.None -> null
        Due.Expired -> "EXPIRED"
        Due.Soon -> when (days) {
            0 -> "EXPIRES TODAY"
            1 -> "EXPIRES TOMORROW"
            else -> "IN $days DAYS"
        }
        Due.Ok -> expires?.let { "EXPIRES ${it.dayOfMonth} ${it.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH).uppercase()} ${it.year % 100}" }
    }
}

enum class Due { None, Ok, Soon, Expired }
