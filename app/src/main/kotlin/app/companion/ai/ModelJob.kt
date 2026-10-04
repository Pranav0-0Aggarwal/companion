package app.companion.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.SystemClock
import app.companion.R
import app.companion.core.Show
import app.companion.system.Notices
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ModelJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var stopped = false

    private fun show(p: JobParameters, n: Notification, end: Int) {
        runCatching { setNotification(p, NOTE, n, end) }
    }

    override fun onStartJob(p: JobParameters): Boolean {
        stopped = false
        show(p, progress(this, DlState(Mode.Running)), END_REMOVE)
        ModelJobs.drop(this)
        scope.launch {
            var last = 0L
            val r = Dl.run(this@ModelJob, { !stopped }) { s ->
                val t = SystemClock.elapsedRealtime()
                if (t - last >= 1000) {
                    last = t
                    show(p, progress(this@ModelJob, s), END_REMOVE)
                }
            }
            when (r) {
                Pull.Done -> done(p, "Models ready")
                Pull.Bad -> done(p, "Download failed: size or checksum does not match")
                Pull.Full -> done(p, "Not enough storage: need ${Show.mbUp(Dl.state.value.need)} MB free")
                Pull.Retry -> {
                    ModelJobs.fallback(this@ModelJob)
                    jobFinished(p, true)
                }
                Pull.Stop -> Unit
            }
        }
        return true
    }

    private fun done(p: JobParameters, text: String) {
        show(p, finished(this, text), END_DETACH)
        jobFinished(p, false)
    }

    override fun onStopJob(p: JobParameters): Boolean {
        stopped = true
        return when (p.stopReason) {
            JobParameters.STOP_REASON_CANCELLED_BY_APP -> false
            JobParameters.STOP_REASON_USER -> {
                Dl.paused = true
                Dl.want(this, false)
                Dl.state.value = DlState(Mode.Paused)
                false
            }
            else -> {
                Dl.state.value = DlState(Mode.Waiting)
                ModelJobs.fallback(this)
                true
            }
        }
    }

    private companion object {
        const val NOTE = 4107
        const val END_DETACH = JobService.JOB_END_NOTIFICATION_POLICY_DETACH
        const val END_REMOVE = JobService.JOB_END_NOTIFICATION_POLICY_REMOVE
    }
}

class ModelCancel : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val p = goAsync()
        val app = c.applicationContext
        thread {
            try {
                ModelJobs.cancel(app)
            } finally {
                p.finish()
            }
        }
    }
}

private const val CHANNEL = "models"

private fun builder(c: Context): Notification.Builder {
    c.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Model download", NotificationManager.IMPORTANCE_LOW))
    return Notification.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_tile).setContentIntent(Notices.open(c))
}

private fun progress(c: Context, s: DlState): Notification {
    val done = s.ready + s.pos
    val pct = Show.pct(done, Dl.total)
    val cancel = PendingIntent.getBroadcast(c, 1, Intent(c, ModelCancel::class.java), PendingIntent.FLAG_IMMUTABLE)
    val text = listOfNotNull("${Show.mb(done)} of ${Show.mb(Dl.total)} MB", Show.speed(s.speed).takeIf { s.speed > 0 }, Show.eta(s.eta)).joinToString(" · ")
    return builder(c)
        .setContentTitle("Downloading models · $pct%")
        .setContentText(text)
        .setProgress(100, pct, false)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(Notification.CATEGORY_PROGRESS)
        .addAction(Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_tile), "Cancel", cancel).build())
        .build()
}

private fun finished(c: Context, text: String) = builder(c).setContentTitle("Companion models").setContentText(text).setAutoCancel(true).build()
