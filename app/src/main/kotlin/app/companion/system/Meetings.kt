package app.companion.system

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.annotation.RequiresApi
import app.companion.core.Clash
import app.companion.core.Face
import app.companion.core.Hidden
import app.companion.core.Meeting
import app.companion.core.Meets
import app.companion.core.MeetPhase
import app.companion.ui.has
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Soon(val next: Meeting?, val first: Meeting?, val clashes: List<Clash>)

object Meetings {
    const val CHAN = "meetings"
    const val LIVE = "meetlive"
    private const val ALARMS = "alarms"
    private const val POSTED = "posted"
    private const val JOINED = "joined"
    private const val DAY = 86_400_000L

    private fun memo(c: Context) = c.getSharedPreferences("meets", Context.MODE_PRIVATE)

    private fun has(c: Context, set: String, k: String) = k in memo(c).getStringSet(set, null).orEmpty()

    private fun put(c: Context, set: String, keys: Set<String>) = memo(c).edit().putStringSet(set, keys).apply()

    private fun add(c: Context, set: String, k: String) = put(c, set, memo(c).getStringSet(set, null).orEmpty() + k)

    fun key(m: Meeting) = "${m.id}:${m.start}"

    private fun begin(k: String) = k.substringAfter(':').toLongOrNull() ?: 0

    private fun tag(k: String) = "meet:$k"

    private fun alarm(c: Context, act: String, k: String): PendingIntent = PendingIntent.getBroadcast(
        c, 0, Intent(c, Meet::class.java).setAction(act).setData(Uri.fromParts("meet", k, null)),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun set(c: Context, at: Long, act: String, k: String) =
        c.getSystemService(AlarmManager::class.java).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm(c, act, k))

    private fun unset(c: Context, act: String, k: String) = c.getSystemService(AlarmManager::class.java).cancel(alarm(c, act, k))

    private fun drop(c: Context, k: String) {
        listOf(Meet.HEADS, Meet.LIVE, Meet.TICK).forEach { unset(c, it, k) }
        c.getSystemService(NotificationManager::class.java).cancel(tag(k), 0)
    }

    private fun active(c: Context, k: String) = c.getSystemService(NotificationManager::class.java).activeNotifications.firstOrNull { it.tag == tag(k) }

    private var watcher: ContentObserver? = null

    fun watch(app: Application) {
        val h = Handler(Looper.getMainLooper())
        val run = Runnable { CoroutineScope(Dispatchers.IO).launch { sync(app); Live.widgets(app) } }
        watcher = object : ContentObserver(h) {
            override fun onChange(selfChange: Boolean) {
                h.removeCallbacks(run)
                h.postDelayed(run, 3_000)
            }
        }.also { app.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, it) }
    }

    fun soon(c: Context, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Soon {
        val o = Prefs.meets(c)
        if (!o.on) return Soon(null, null, emptyList())
        val all = Cals.meetings(c, now - Meets.LINGER, now + Meets.AHEAD, o)
        val end = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val next = all.firstOrNull { Meets.wanted(it, o) && it.start - now <= Meets.SOON }
        val first = all.firstOrNull { Meets.wanted(it, o) && it.start >= now && it.start < end }
        return Soon(next, first, Meets.clashes(all.filter { it.start < end }).filter { it.until > now })
    }

    suspend fun sync(c: Context) = withContext(Dispatchers.IO) {
        val o = Prefs.meets(c)
        val now = System.currentTimeMillis()
        val list = if (o.on) Cals.meetings(c, now - Meets.LINGER, now + Meets.AHEAD, o).filter { Meets.wanted(it, o) } else emptyList()
        val keep = list.map(::key).toSet()
        memo(c).getStringSet(ALARMS, null).orEmpty().filter { it !in keep }.forEach { drop(c, it) }
        put(c, ALARMS, keep)
        listOf(POSTED, JOINED).forEach { s -> put(c, s, memo(c).getStringSet(s, null).orEmpty().filter { begin(it) > now - DAY }.toSet()) }
        list.forEach { plan(c, it, now) }
    }

    private fun plan(c: Context, m: Meeting, now: Long) {
        val o = Prefs.meets(c)
        val k = key(m)
        if (has(c, JOINED, k)) return
        val lead = Meets.lead(m, o)
        val covered = Meets.covered(lead, Cals.reminders(c, m.id))
        val heads = m.start - lead * Meets.MIN
        unset(c, Meet.HEADS, k)
        if (!covered && heads > now) set(c, heads, Meet.HEADS, k)
        if (m.start - Meets.LIVE > now) set(c, m.start - Meets.LIVE, Meet.LIVE, k)
        if (has(c, POSTED, k)) return
        when (Meets.step(m.start, lead, now)) {
            MeetPhase.Heads -> if (covered) { if (Meets.ongoing(m.start, now)) show(c, m, false, false, now) } else show(c, m, true, true, now)
            MeetPhase.Live -> show(c, m, false, false, now)
            else -> Unit
        }
    }

    suspend fun fire(c: Context, act: String, id: Long, begin: Long) = withContext(Dispatchers.IO) {
        val o = Prefs.meets(c)
        val k = "$id:$begin"
        val now = System.currentTimeMillis()
        if (!o.on || has(c, JOINED, k) || now >= begin + Meets.LINGER) return@withContext
        val m = Cals.meetings(c, begin, begin + 1, o).firstOrNull { it.id == id && it.start == begin }?.takeIf { Meets.wanted(it, o) }
        if (m == null) {
            drop(c, k)
            return@withContext
        }
        when (act) {
            Meet.HEADS -> show(c, m, true, true, now)
            Meet.LIVE -> show(c, m, false, false, now)
            Meet.TICK -> active(c, k)?.let { show(c, m, it.notification.channelId == CHAN, false, now) }
        }
        Live.widgets(c)
    }

    private fun show(c: Context, m: Meeting, heads: Boolean, alert: Boolean, now: Long) {
        if (!c.has(Manifest.permission.POST_NOTIFICATIONS)) return
        val k = key(m)
        val mins = Meets.mins(m.start, now)
        val ongoing = Meets.ongoing(m.start, now)
        val full = Face(m.title.ifBlank { "Meeting" }, listOfNotNull(Meets.until(m.start, now), Meets.source(m)).joinToString(" · "))
        val b = Ping.base(c, if (heads) CHAN else LIVE, full, Hidden.meet, Prefs.get(c))
            .setCategory(Notification.CATEGORY_EVENT)
            .setWhen(m.start)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(!alert)
            .setTimeoutAfter(m.start + Meets.LINGER - now)
            .setContentIntent(m.join?.let { join(c, k, it.url) } ?: Notes.to(c, "today"))
        m.join?.let { b.addAction(Ping.action(c, "Join", join(c, k, it.url))) }
        m.place?.let { b.addAction(Ping.action(c, "Directions", view(c, "dir$k", directions(it)))) }
        b.addAction(Ping.action(c, "Running late", view(c, "late$k", late())))
        if (ongoing) b.setOngoing(true).setUsesChronometer(true).setChronometerCountDown(true)
        val promote = ongoing && Build.VERSION.SDK_INT >= 36
        if (promote) promote(b, Meets.chip(mins))
        c.getSystemService(NotificationManager::class.java).notify(tag(k), 0, b.build())
        add(c, POSTED, k)
        if (promote && mins >= 1) set(c, m.start - (mins - 1) * Meets.MIN, Meet.TICK, k)
    }

    @RequiresApi(36)
    private fun promote(b: Notification.Builder, chip: String) {
        if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) b.setRequestPromotedOngoing(true).setShortCriticalText(chip)
    }

    private fun join(c: Context, k: String, url: String) = PendingIntent.getActivity(
        c, "join$k".hashCode(),
        Intent(c, Joined::class.java).setData(Uri.fromParts("meet", k, null)).putExtra(Joined.KEY, k).putExtra(Joined.URL, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun view(c: Context, code: String, i: Intent) =
        PendingIntent.getActivity(c, code.hashCode(), i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun directions(place: String) = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(place)}"))

    private fun late() = Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Meets.LATE), null)

    fun open(c: Context, k: String, url: String) {
        add(c, JOINED, k)
        c.getSystemService(NotificationManager::class.java).cancel(tag(k), 0)
        go(c, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun open(c: Context, m: Meeting) = m.join?.let { open(c, key(m), it.url) }

    fun go(c: Context, i: Intent) {
        runCatching { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun stop(c: Context) {
        memo(c).getStringSet(ALARMS, null).orEmpty().forEach { drop(c, it) }
        memo(c).edit().clear().commit()
    }
}
