package app.companion.system

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.companion.core.Alerts
import app.companion.data.dueDate
import app.companion.sl
import app.companion.ui.dateOf
import app.companion.ui.zone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Act : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getLongExtra(ID, 0)
        if (i.action != REMIND || id <= 0) return
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val b = c.sl.repo.item(id) ?: return@launch
                val now = System.currentTimeMillis()
                Plan.remind(c, b.title, Alerts.remindAt(b.dueDate ?: dateOf(now), now, zone()), "bill")
                c.getSystemService(NotificationManager::class.java).cancel(Ping.BILL, id.toInt())
            } finally {
                done.finish()
            }
        }
    }

    companion object {
        const val ID = "id"
        const val REMIND = "app.companion.REMIND"

        fun remind(c: Context, id: Long): PendingIntent = PendingIntent.getBroadcast(
            c, id.toInt(), Intent(c, Act::class.java).setAction(REMIND).putExtra(ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
