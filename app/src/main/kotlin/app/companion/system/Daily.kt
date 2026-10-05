package app.companion.system

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.companion.core.Digest
import app.companion.core.Face
import app.companion.core.Hidden
import app.companion.data.dueDate
import app.companion.sl
import app.companion.ui.daysTo
import app.companion.ui.has
import app.companion.ui.inr

class Daily(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        val o = Prefs.get(c)
        if (!o.digest || !c.has(Manifest.permission.POST_NOTIFICATIONS)) return Result.success()
        val repo = c.sl.repo
        val now = System.currentTimeMillis()
        val days = repo.billsNow().mapNotNull { b -> b.dueDate?.let { daysTo(it) } }
        val (total, n) = repo.spentToday(now)
        val text = Digest.text(repo.needs(now - 3 * 86_400_000L), days.count { it == 0 }, days.count { it == 1 }, if (n > 0) inr(total) else null) ?: return Result.success()
        val note = Ping.base(c, Ping.DIGEST, Face("Today", text), Hidden.daily, o)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(Notes.to(c, "inbox"))
            .build()
        c.getSystemService(NotificationManager::class.java).notify(Ping.DIGEST, 0, note)
        Live.widgets(c)
        return Result.success()
    }
}
