package app.companion.system

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.companion.core.Brief
import app.companion.core.Hidden
import app.companion.core.Meets
import app.companion.core.Stage
import app.companion.data.dueDate
import app.companion.sl
import app.companion.ui.daysTo
import app.companion.ui.has
import app.companion.ui.hm
import app.companion.ui.money
import app.companion.ui.weekday

class Morning(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        try {
            post()
        } finally {
            Live.brief(applicationContext)
        }
        return Result.success()
    }

    private suspend fun post() {
        val c = applicationContext
        if (!Prefs.brief(c).on || !c.has(Manifest.permission.POST_NOTIFICATIONS)) return
        val o = Prefs.get(c)
        val repo = c.sl.repo
        val now = System.currentTimeMillis()
        val soon = Meetings.soon(c, now)
        val bills = repo.billsNow().mapNotNull { b -> b.dueDate?.let(::daysTo)?.takeIf { it in 0..3 }?.let { b to it } }.take(2)
        val orders = if (o.delivery) Ping.orders(c, 0, now).filter { it.stage == Stage.Out || it.stage == Stage.Shipped } else emptyList()
        val parts = listOfNotNull(soon.first?.let { Brief.meeting(hm(it.start)) }, soon.clashes.firstOrNull()?.let { Meets.clash(it, ::hm) }) +
            bills.map { (b, d) -> Brief.bill(b.title, b.paise.takeIf { it > 0 }?.let { money(it, b.currency) }, d, weekday(b.dueDate!!)) } +
            listOfNotNull(orders.firstOrNull()?.let { Brief.orders(it.merchant, orders.size) })
        val face = Brief.face(repo.profileNow().name, parts) ?: return
        val note = Ping.base(c, Ping.DIGEST, face, Hidden.brief, o)
            .setStyle(Notification.BigTextStyle().bigText(face.text))
            .setCategory(Notification.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(Notes.to(c, "today"))
            .build()
        c.getSystemService(NotificationManager::class.java).notify(Ping.DIGEST, 1, note)
        Live.widgets(c)
    }
}
