package app.companion.system

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import app.companion.core.Suggestion
import app.companion.ui.has
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

data class CalEvent(val title: String, val start: Long, val allDay: Boolean, val place: String)

data class CalChoice(val id: Long, val name: String, val account: String)

object Cals {
    fun today(c: Context): List<CalEvent> {
        if (!c.has(Manifest.permission.READ_CALENDAR)) return emptyList()
        val zone = ZoneId.systemDefault()
        val day = LocalDate.now(zone)
        val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val cols = arrayOf(
            CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        return c.contentResolver.query(uri, cols, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { q ->
            buildList {
                while (q.moveToNext()) {
                    val allDay = q.getInt(2) == 1
                    val begin = q.getLong(1)
                    if (allDay && Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate() != day) continue
                    add(CalEvent(q.getString(0).orEmpty(), begin, allDay, q.getString(3).orEmpty()))
                }
            }
        } ?: emptyList()
    }

    fun choices(c: Context): List<CalChoice> {
        if (!c.has(Manifest.permission.READ_CALENDAR)) return emptyList()
        val cols = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME)
        val sel = "${CalendarContract.Calendars.VISIBLE} = 1 AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        return c.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, cols, sel, arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()), null,
        )?.use { q ->
            buildList { while (q.moveToNext()) add(CalChoice(q.getLong(0), q.getString(1).orEmpty(), q.getString(2).orEmpty())) }
        } ?: emptyList()
    }

    fun add(c: Context, cal: Long?, e: Suggestion.Cal): Boolean {
        val target = cal ?: choices(c).firstOrNull()?.id
        if (target == null || !c.has(Manifest.permission.WRITE_CALENDAR)) {
            c.startActivity(insert(e))
            return false
        }
        val zone = ZoneId.systemDefault()
        val v = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, target)
            put(CalendarContract.Events.TITLE, e.title)
            put(CalendarContract.Events.DESCRIPTION, e.note)
            if (e.allDay) {
                val s = Instant.ofEpochMilli(e.start).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                put(CalendarContract.Events.DTSTART, s)
                put(CalendarContract.Events.DTEND, s + 86_400_000L)
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            } else {
                put(CalendarContract.Events.DTSTART, e.start)
                put(CalendarContract.Events.DTEND, e.end)
                put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            }
        }
        return c.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v) != null
    }

    private fun insert(e: Suggestion.Cal) = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, e.title)
        .putExtra(CalendarContract.Events.DESCRIPTION, e.note)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.start)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end)
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, e.allDay)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
