package app.companion.link

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.service.notification.StatusBarNotification
import app.companion.Services
import app.companion.data.dueDate
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class LinkEvents(private val c: Context, private val sl: Services, private val shares: () -> Set<String>, private val out: (Obj) -> Unit) {
    private var scope: CoroutineScope? = null
    private val otps = HashSet<Long>()
    private val alerts = HashSet<Long>()
    private var seeded = false
    private var level = -1
    private var charging = false
    private var cell = false
    private var watching = false
    private var focused = false
    private val cm = c.getSystemService(ClipboardManager::class.java)

    fun start(sc: CoroutineScope) {
        stop()
        scope = sc
        sc.launch { sl.repo.changes("items").debounce(SCAN).collect { scan() } }
        sc.launch { sl.repo.changes("items").debounce(DIRTY).collect { if ("money" in shares()) listOf("today", "bills", "ledger").forEach(::dirty) } }
        sc.launch { sl.repo.changes("tasks").debounce(DIRTY).collect { if ("tasks" in shares()) listOf("tasks", "today").forEach(::dirty) } }
        sync()
    }

    fun stop() {
        scope = null
        otps.clear()
        alerts.clear()
        seeded = false
        level = -1
        watch(false)
        clip(false)
    }

    fun sync() {
        val on = shares()
        if ("otp" !in on) otps.clear()
        if ("alerts" !in on) {
            alerts.clear()
            seeded = false
        }
        watch("battery" in on)
        clip(focused && "clipOut" in on)
        scope?.launch { scan() }
    }

    fun focus(on: Boolean) {
        focused = on
        clip(scope != null && on && "clipOut" in shares())
    }

    fun notif(sbn: StatusBarNotification, importance: () -> Int) {
        val n = sbn.notification
        val pkg = sbn.packageName
        if (scope == null || "notifs" !in shares() || pkg !in LinkStore.apps(c) || importance() < NotificationManager.IMPORTANCE_DEFAULT) return
        if (n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_GROUP_SUMMARY) != 0) return
        val x = n.extras
        val title = x.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (x.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: x.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty().take(TEXT)
        if (title.isEmpty() && text.isEmpty()) return
        val name = runCatching { c.packageManager.getApplicationInfo(pkg, 0).loadLabel(c.packageManager).toString() }.getOrDefault(pkg)
        send(mapOf("t" to "notif", "id" to sbn.key, "app" to pkg, "appName" to name, "title" to title, "text" to text, "at" to sbn.postTime))
    }

    private fun send(o: Obj) {
        scope?.launch { out(o) }
    }

    private fun dirty(what: String) = send(mapOf("t" to "dirty", "what" to what))

    private suspend fun scan() {
        val on = shares()
        val repo = sl.repo
        if ("otp" in on) {
            val live = repo.otpsNow().associateBy { it.id }
            live.filterKeys { otps.add(it) }.values.forEach { out(mapOf("t" to "otp") + LinkShape.otp(it)) }
            otps.filter { it !in live }.forEach {
                otps.remove(it)
                out(mapOf("t" to "otp_gone", "item" to it))
            }
        }
        if ("alerts" in on) {
            val now = System.currentTimeMillis()
            val soon = LocalDate.now(ZoneId.systemDefault()).plusDays(BILL_DAYS)
            val fresh = repo.needing(NEEDS).map { it to LinkShape.alert(it) } + repo.billsNow().filter { it.dueDate?.let { d -> !d.isAfter(soon) } == true }.map { it to LinkShape.due(it) }
            fresh.filter { (i, _) -> alerts.add(i.id) && (seeded || i.at >= now - RECENT) }.forEach { (_, a) -> out(mapOf("t" to "alert") + a) }
            seeded = true
        }
    }

    private val power = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = battery(i)
    }

    private fun watch(on: Boolean) {
        if (on == watching) return
        watching = on
        if (on) {
            c.registerReceiver(power, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)?.let(::battery)
        } else {
            runCatching { c.unregisterReceiver(power) }
            level = -1
        }
    }

    private fun battery(i: Intent) {
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val raw = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        if (scale <= 0 || raw < 0) return
        val pct = raw * 100 / scale
        val chg = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1).let { it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL }
        if (level >= 0 && pct / 5 == level / 5 && chg == charging) return
        level = pct
        charging = chg
        send(mapOf("t" to "battery", "pct" to pct, "charging" to chg))
    }

    private val copied = ClipboardManager.OnPrimaryClipChangedListener {
        val d = cm.primaryClipDescription
        if (d == null || d.label == "code" || d.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true) return@OnPrimaryClipChangedListener
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(c)?.toString()?.take(CLIP)?.takeIf { it.isNotBlank() && it != echo } ?: return@OnPrimaryClipChangedListener
        send(mapOf("t" to "clip", "text" to text))
    }

    private fun clip(on: Boolean) {
        if (on == cell) return
        cell = on
        if (on) cm.addPrimaryClipChangedListener(copied) else cm.removePrimaryClipChangedListener(copied)
    }

    companion object {
        @Volatile var echo: String? = null
        private const val SCAN = 300L
        private const val DIRTY = 2_000L
        private const val RECENT = 2 * 3_600_000L
        private const val BILL_DAYS = 3L
        private const val NEEDS = 30
        private const val TEXT = 500
        private const val CLIP = 20_000
    }
}
