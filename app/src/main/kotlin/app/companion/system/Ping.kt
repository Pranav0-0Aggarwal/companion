package app.companion.system

import app.companion.ui.kit.BrandMark
import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import app.companion.R
import app.companion.core.Alerts
import app.companion.core.Chan
import app.companion.core.Face
import app.companion.core.Hidden
import app.companion.core.Kind
import app.companion.core.Opts
import app.companion.core.Stage
import app.companion.core.Track
import app.companion.data.Item
import app.companion.data.Repo
import app.companion.data.State
import app.companion.data.credit
import app.companion.data.dueDate
import app.companion.sl
import app.companion.ui.codeText
import app.companion.ui.has
import app.companion.ui.inr
import app.companion.ui.money
import app.companion.ui.screens.CRED
import app.companion.ui.shortDay
import java.time.ZoneId

object Ping {
    const val SPENDS = "spends"
    const val DELIVERIES = "deliveries"
    const val DIGEST = "digest"
    const val BILL = "bill"
    private const val SPEND = "spend"
    private const val ORDER = "order"
    private const val SUMMARY = 0

    data class Order(val key: String, val merchant: String?, val stage: Stage, val at: Long, val id: Long, val ping: Int, val code: String? = null)

    private val Item.chan get() = Kind.entries.firstOrNull { it.name == kind }?.let(Alerts::chan)

    private fun icon(c: Context) = Icon.createWithResource(c, R.drawable.ic_tile)

    private fun mark(c: Context, name: String) = Icon.createWithBitmap(BrandMark.bitmap(c, name, (48 * c.resources.displayMetrics.density).toInt()))

    fun action(c: Context, label: String, pi: PendingIntent) = Notification.Action.Builder(icon(c), label, pi).build()

    private fun quiet(c: Context, chan: String, f: Face) =
        Notification.Builder(c, chan).setSmallIcon(R.drawable.ic_tile).setContentTitle(f.title).setContentText(f.text).build()

    fun base(c: Context, chan: String, full: Face, hidden: Face, o: Opts): Notification.Builder =
        Notification.Builder(c, chan)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(full.title)
            .setContentText(full.text)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(quiet(c, chan, Alerts.shown(o, full, hidden)))

    private fun live(nm: NotificationManager, tag: String, id: Int) = nm.activeNotifications.any { it.tag == tag && it.id == id }

    suspend fun sync(c: Context, again: Set<Long> = emptySet()) {
        if (!c.has(Manifest.permission.POST_NOTIFICATIONS)) return
        val o = Prefs.get(c)
        val repo = c.sl.repo
        val nm = c.getSystemService(NotificationManager::class.java)
        val now = System.currentTimeMillis()
        val since = maxOf(now - Alerts.FRESH, Prefs.since(c))
        if (o.spend) spends(c, repo, nm, o, since, again)
        if (o.bill) bills(c, repo, nm, o, since, now, again)
        if (o.delivery) deliveries(c, repo, nm, o, since, now)
    }

    private suspend fun spends(c: Context, repo: Repo, nm: NotificationManager, o: Opts, since: Long, again: Set<Long>) {
        val bit = Alerts.bit(Chan.Spend)
        val redo = again.mapNotNull { repo.item(it) }.filter { it.chan == Chan.Spend && it.ping and bit != 0 && live(nm, SPEND, it.id.toInt()) }
        val list = (repo.unpinged(Chan.Spend, since) + redo).distinctBy { it.id }
        if (list.isEmpty()) return
        for (i in list) {
            val full = Alerts.spend(money(i.paise, i.currency), i.title, i.category, i.bank, i.last4, i.credit)
            val open = Notes.to(c, "ledger", i.id)
            val n = base(c, SPENDS, full, Hidden.spend, o)
                .setLargeIcon(mark(c, i.title))
                .setGroup(SPEND)
                .setWhen(i.at)
                .setShowWhen(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentIntent(open)
                .addAction(action(c, "Change category", open))
                .build()
            nm.notify(SPEND, i.id.toInt(), n)
            if (i.ping and bit == 0) repo.addPing(i.id, bit)
        }
        val (total, n) = repo.spentToday()
        if (n == 0) return
        val sum = base(c, SPENDS, Alerts.total(inr(total), n), Hidden.spend, o)
            .setGroup(SPEND)
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(Notes.to(c, "ledger"))
            .build()
        nm.notify(SPEND, SUMMARY, sum)
    }

    fun bill(c: Context, b: Item, full: Face, hidden: Face, o: Opts, once: Boolean = true): Notification {
        val cred = c.packageManager.getLaunchIntentForPackage(CRED)
        val pay = if (cred != null) PendingIntent.getActivity(c, b.id.toInt(), cred, PendingIntent.FLAG_IMMUTABLE) else Notes.to(c, "bills", b.id)
        return base(c, Live.BILLS, full, hidden, o)
            .setLargeIcon(mark(c, b.merchant ?: b.title))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setOnlyAlertOnce(once)
            .setAutoCancel(true)
            .setContentIntent(Notes.to(c, "bills", b.id))
            .addAction(action(c, "Pay", pay))
            .addAction(action(c, "Remind me", Act.remind(c, b.id)))
            .build()
    }

    fun face(b: Item) = Alerts.bill(b.title, b.paise.takeIf { it > 0 }?.let { money(it, b.currency) }, b.dueDate?.let(::shortDay))

    private suspend fun bills(c: Context, repo: Repo, nm: NotificationManager, o: Opts, since: Long, now: Long, again: Set<Long>) {
        val bit = Alerts.bit(Chan.Bill)
        for (g in repo.cycles()) {
            val b = g.first()
            if (b.state == State.ASK || b.at < since || !Alerts.fresh(b.at, now)) continue
            val seen = b.ping and bit != 0
            val redo = seen && b.id in again && live(nm, BILL, b.id.toInt())
            if (seen && !redo) continue
            if (redo || g.none { it.id != b.id && it.ping and bit != 0 }) nm.notify(BILL, b.id.toInt(), bill(c, b, face(b), Hidden.bill, o))
            if (!seen) repo.addPing(b.id, bit)
        }
    }

    suspend fun orders(c: Context, since: Long, now: Long = System.currentTimeMillis()): List<Order> {
        val repo = c.sl.repo
        val zone = ZoneId.systemDefault()
        val rows = repo.deliveriesSince(maxOf(now - Alerts.FRESH, since))
        val found = rows.groupBy { Track.key(it.merchant, it.at, zone) }.mapNotNull { (k, l) ->
            val by = l.mapNotNull { i -> Track.stage(i.title)?.let { it to i } }
            val st = Track.latest(by.map { it.first }) ?: return@mapNotNull null
            val i = by.last { it.first == st }.second
            Order(k, i.merchant, st, i.at, i.id, i.ping).takeIf { st != Stage.Out || i.at + Track.OUT > now }
        }
        val otps = repo.otpsNow(now).filter { it.note == "delivery" && it.code != null }
        val outs = found.count { it.stage == Stage.Out }
        return found.map { o ->
            if (o.stage != Stage.Out) o else o.copy(code = (otps.firstOrNull { Track.same(it.title, o.merchant) } ?: otps.singleOrNull()?.takeIf { outs == 1 })?.code)
        }
    }

    private suspend fun deliveries(c: Context, repo: Repo, nm: NotificationManager, o: Opts, since: Long, now: Long) {
        val bit = Alerts.bit(Chan.Delivery)
        for (r in orders(c, since, now)) {
            val id = r.key.hashCode() and 0xFFFF
            val out = r.stage == Stage.Out
            when {
                r.ping and bit != 0 -> if (out && live(nm, ORDER, id)) nm.notify(ORDER, id, ship(c, r, o, now))
                out || r.stage == Stage.Delivered && Alerts.fresh(r.at, now) -> {
                    nm.notify(ORDER, id, ship(c, r, o, now))
                    repo.addPing(r.id, bit)
                }
            }
        }
    }

    private fun ship(c: Context, r: Order, o: Opts, now: Long): Notification {
        val out = r.stage == Stage.Out
        val who = r.merchant?.let { "$it order" } ?: "Order"
        val full = Face(if (out) "$who out for delivery" else "$who delivered", r.code?.takeIf { out }?.let { "Delivery code ${codeText(it)}" })
        val b = base(c, DELIVERIES, full, Hidden.order, o)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(out)
            .setAutoCancel(!out)
            .setOnlyAlertOnce(true)
            .setWhen(r.at)
            .setShowWhen(true)
            .setContentIntent(Notes.to(c, "today"))
            .setTimeoutAfter(if (out) r.at + Track.OUT - now else Track.DONE)
        if (out && r.code != null) b.addAction(action(c, "Copy code", Notes.copy(c, r.code)))
        val at = Track.at(r.stage) ?: 0
        if (Build.VERSION.SDK_INT >= 36) track(c, b, at, out, if (o.lock && r.code != null && r.code.length <= 7) r.code else "Out")
        else b.setProgress(Track.MAX, at, false)
        return b.build()
    }

    @RequiresApi(36)
    private fun track(c: Context, b: Notification.Builder, at: Int, out: Boolean, chip: String) {
        val cuts = listOf(0) + Track.points + Track.MAX
        b.setStyle(
            Notification.ProgressStyle()
                .setProgressSegments(cuts.zipWithNext { a, z -> Notification.ProgressStyle.Segment(z - a) })
                .setProgressPoints(Track.points.map { Notification.ProgressStyle.Point(it) })
                .setProgress(at)
                .setProgressTrackerIcon(icon(c)),
        )
        if (out && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) b.setRequestPromotedOngoing(true).setShortCriticalText(chip)
    }
}
