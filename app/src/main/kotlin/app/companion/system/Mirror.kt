package app.companion.system

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.companion.core.Agenda
import app.companion.core.Have
import app.companion.core.Want
import app.companion.data.State
import app.companion.data.cal
import app.companion.data.dueDate
import app.companion.sl
import app.companion.ui.has
import app.companion.ui.money
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Mirror {
    private const val WORK = "mirror"
    private const val BLUE = 0xFF2A62DB
    private val perms = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    fun ready(c: Context) = perms.all(c::has)

    private fun synced(u: Uri): Uri = u.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, Cals.OWN)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    fun queue(c: Context) {
        if (!Prefs.mirror(c)) return
        WorkManager.getInstance(c).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<CalSync>().setInitialDelay(15, TimeUnit.SECONDS).build())
    }

    fun drop(c: Context) {
        if (!c.has(Manifest.permission.WRITE_CALENDAR)) return
        c.contentResolver.delete(synced(Calendars.CONTENT_URI), Cals.local, null)
    }

    private fun create(c: Context): Long? {
        val v = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, Cals.OWN)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, Cals.OWN)
            put(Calendars.CALENDAR_DISPLAY_NAME, Cals.OWN)
            put(Calendars.CALENDAR_COLOR, BLUE.toInt())
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, Cals.OWN)
            put(Calendars.CALENDAR_TIME_ZONE, ZoneId.systemDefault().id)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
        }
        return c.contentResolver.insert(synced(Calendars.CONTENT_URI), v)?.let(ContentUris::parseId)
    }

    private fun have(c: Context, cal: Long): List<Have> {
        val cols = arrayOf(Events._ID, Events.SYNC_DATA1, Events.TITLE, Events.DESCRIPTION, Events.DTSTART, Events.DTEND, Events.ALL_DAY)
        return c.contentResolver.query(Events.CONTENT_URI, cols, "${Events.CALENDAR_ID} = ? AND ${Events.DELETED} = 0", arrayOf(cal.toString()), null)?.use { q ->
            buildList {
                while (q.moveToNext()) {
                    val key = q.getString(1) ?: continue
                    add(Have(q.getLong(0), Want(key, q.getString(2).orEmpty(), q.getLong(4), q.getLong(5), q.getInt(6) == 1, q.getString(3).orEmpty())))
                }
            }
        } ?: emptyList()
    }

    private fun values(w: Want, cal: Long? = null) = ContentValues().apply {
        cal?.let { put(Events.CALENDAR_ID, it) }
        put(Events.TITLE, w.title)
        put(Events.DESCRIPTION, w.note)
        put(Events.DTSTART, w.start)
        put(Events.DTEND, w.end)
        put(Events.ALL_DAY, if (w.allDay) 1 else 0)
        put(Events.EVENT_TIMEZONE, if (w.allDay) "UTC" else ZoneId.systemDefault().id)
        put(Events.AVAILABILITY, Events.AVAILABILITY_FREE)
        put(Events.SYNC_DATA1, w.key)
    }

    suspend fun sync(c: Context) = withContext(Dispatchers.IO) {
        if (!Prefs.mirror(c) || !ready(c)) return@withContext
        val cal = Cals.own(c) ?: create(c) ?: return@withContext
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val repo = c.sl.repo
        val bills = repo.cycles().mapNotNull { g ->
            val b = g.first()
            val due = b.dueDate?.takeIf { b.state != State.ASK && it >= today } ?: return@mapNotNull null
            Agenda.bill("b:${g.last().id}", b.title, b.paise.takeIf { it > 0 }?.let { money(it, b.currency) }, due)
        }
        val trips = repo.mirrored().mapNotNull { i -> i.cal()?.let { Agenda.trip("t:${i.id}", it, zone) } }
        val e = Agenda.diff(have(c, cal), bills + trips)
        val events = synced(Events.CONTENT_URI)
        val r = c.contentResolver
        e.add.forEach { r.insert(events, values(it, cal)) }
        e.change.forEach { r.update(ContentUris.withAppendedId(events, it.row), values(it.want), null, null) }
        e.drop.forEach { r.delete(ContentUris.withAppendedId(events, it), null, null) }
    }
}

class CalSync(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        Mirror.sync(applicationContext)
        return Result.success()
    }
}
