package app.companion.system

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.companion.core.Face
import app.companion.core.Hidden
import app.companion.data.dueDate
import app.companion.sl
import app.companion.ui.Voice
import app.companion.ui.daysTo
import app.companion.ui.has
import app.companion.ui.money

class Reminders(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        if (!c.has(Manifest.permission.POST_NOTIFICATIONS)) return Result.success()
        val o = Prefs.get(c)
        if (!o.remind) return Result.success()
        val repo = c.sl.repo
        val name = repo.profileNow().name
        val nm = c.getSystemService(NotificationManager::class.java)
        for (b in repo.billsNow()) {
            val days = daysTo(b.dueDate ?: continue)
            val bit = when (days) {
                0 -> 4
                1 -> 2
                2, 3 -> 1
                else -> continue
            }
            if (b.ping and bit != 0) continue
            val full = Face(Voice.due(name, b.title, days), if (b.paise > 0) money(b.paise, b.currency) else null)
            nm.notify(Ping.BILL, b.id.toInt(), Ping.bill(c, b, full, Hidden.due, o))
            repo.addPing(b.id, bit)
        }
        Live.widgets(c)
        return Result.success()
    }
}
