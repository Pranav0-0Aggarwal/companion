package app.companion.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

data class Want(val key: String, val title: String, val start: Long, val end: Long, val allDay: Boolean, val note: String = "")

data class Have(val row: Long, val want: Want)

data class Edits(val add: List<Want>, val change: List<Have>, val drop: List<Long>)

object Agenda {
    private const val DAY = 86_400_000L

    private fun utc(d: LocalDate) = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun bill(key: String, title: String, amount: String?, due: LocalDate): Want =
        Want(key, listOfNotNull(title, amount).joinToString(" ") + " due", utc(due), utc(due) + DAY, true)

    fun trip(key: String, c: Suggestion.Cal, zone: ZoneId): Want =
        if (c.allDay) utc(Instant.ofEpochMilli(c.start).atZone(zone).toLocalDate()).let { Want(key, c.title, it, it + DAY, true, c.note) }
        else Want(key, c.title, c.start, c.end, false, c.note)

    fun diff(have: List<Have>, want: List<Want>): Edits {
        val rows = have.groupBy { it.want.key }
        val keep = rows.mapValues { it.value.first() }
        val wanted = want.associateBy { it.key }
        return Edits(
            add = wanted.values.filter { it.key !in keep },
            change = keep.values.filter { h -> wanted[h.want.key]?.let { it != h.want } == true }.map { h -> Have(h.row, wanted.getValue(h.want.key)) },
            drop = rows.flatMap { (k, l) -> if (k in wanted) l.drop(1) else l }.map { it.row },
        )
    }
}
