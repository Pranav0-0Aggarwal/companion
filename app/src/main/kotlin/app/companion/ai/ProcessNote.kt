package app.companion.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import androidx.work.ForegroundInfo
import app.companion.R
import app.companion.core.Show
import app.companion.system.Notices
import app.companion.ui.has
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object Note {
    const val PAUSE = "app.companion.PROC_PAUSE"
    const val RESUME = "app.companion.PROC_RESUME"
    const val CANCEL = "app.companion.PROC_CANCEL"
    private const val CHANNEL = "process"
    private const val RUN = 4108
    private const val END = 4109

    private fun builder(c: Context): Notification.Builder {
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Processing", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_tile).setContentIntent(Notices.open(c))
    }

    private fun act(c: Context, label: String, action: String, code: Int): Notification.Action {
        val pi = PendingIntent.getBroadcast(c, code, Intent(c, ProcessAct::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_tile), label, pi).build()
    }

    private fun word(job: Job?) = if (job == Job.Import) "Importing messages" else "Reprocessing messages"

    private fun post(c: Context, id: Int, n: Notification) {
        if (c.has(android.Manifest.permission.POST_NOTIFICATIONS)) c.getSystemService(NotificationManager::class.java).notify(id, n)
    }

    fun progress(c: Context, r: Run?): Notification {
        val hold = r?.takeIf { it.paused }?.reason
        val count = r?.let { "${it.done} of ${it.total}" }.orEmpty()
        return builder(c)
            .setContentTitle(if (hold != null) "Paused · $hold" else word(r?.job))
            .setContentText(if (hold != null || r == null) count else "$count · ${Show.eta(r.etaSec ?: -1)}")
            .setProgress(100, r?.let { Show.pct(it.done.toLong(), it.total.toLong()) } ?: 0, r == null || r.total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(act(c, "Pause", PAUSE, 1))
            .addAction(act(c, "Cancel", CANCEL, 2))
            .build()
    }

    fun info(c: Context, r: Run?) = ForegroundInfo(RUN, progress(c, r), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

    fun paused(c: Context, r: Run) = post(
        c, END,
        builder(c)
            .setContentTitle("Paused · ${word(r.job).lowercase()}")
            .setContentText("${r.done} of ${r.total}")
            .addAction(act(c, "Resume", RESUME, 3))
            .addAction(act(c, "Cancel", CANCEL, 2))
            .build(),
    )

    fun done(c: Context, job: Job, text: String) = post(
        c, END,
        builder(c).setContentTitle(if (job == Job.Import) "Import finished" else "Reprocessing finished").setContentText(text).setAutoCancel(true).build(),
    )

    fun clear(c: Context) {
        val m = c.getSystemService(NotificationManager::class.java)
        m.cancel(RUN)
        m.cancel(END)
    }
}

class ProcessAct : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val action = i.action ?: return
        val app = c.applicationContext
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    Note.PAUSE -> Processing.halt(app)
                    Note.RESUME -> Processing.again(app)
                    Note.CANCEL -> Processing.end(app)
                }
            } finally {
                done.finish()
            }
        }
    }
}
