package app.companion.system

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.companion.sl
import app.companion.core.Alerts
import app.companion.system.widget.CoverWidget
import app.companion.system.widget.OtpWidget
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

object Live {
    const val OTP = "otp"
    const val BILLS = "bills"
    private const val SWEEP = "sweep"

    fun boot(app: Application) {
        app.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(OTP, "Codes", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(BILLS, "Bills", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(Ping.SPENDS, "Spending", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(Ping.DELIVERIES, "Deliveries", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(Ping.DIGEST, "Daily summary", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(Meetings.CHAN, "Meetings", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(Meetings.LIVE, "Meeting countdown", NotificationManager.IMPORTANCE_LOW),
            ),
        )
        val daily = PeriodicWorkRequestBuilder<Reminders>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork("reminders", ExistingPeriodicWorkPolicy.KEEP, daily)
        Prefs.since(app)
        digest(app)
        val retain = PeriodicWorkRequestBuilder<Sweep>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork("retain", ExistingPeriodicWorkPolicy.KEEP, retain)
    }

    fun digest(c: Context) {
        val req = OneTimeWorkRequestBuilder<Daily>()
            .setInitialDelay(Alerts.untilDigest(System.currentTimeMillis(), ZoneId.systemDefault()), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(c).enqueueUniqueWork("digest", ExistingWorkPolicy.REPLACE, req)
    }

    suspend fun widgets(c: Context) {
        OtpWidget().updateAll(c)
        CoverWidget().updateAll(c)
    }

    suspend fun refresh(c: Context, again: Set<Long> = emptySet()) {
        val repo = c.sl.repo
        repo.sweep()
        val live = repo.otpsNow()
        val work = WorkManager.getInstance(c)
        val newest = live.firstOrNull()
        if (newest == null) {
            c.getSystemService(NotificationManager::class.java).cancel(Notes.OTP)
            work.cancelUniqueWork(SWEEP)
        } else {
            Notes.otp(c, newest, repo.profileNow().name)
            val wait = live.minOf { it.expires ?: Long.MAX_VALUE } - System.currentTimeMillis()
            val sweep = OneTimeWorkRequestBuilder<Sweep>().setInitialDelay(wait, TimeUnit.MILLISECONDS).build()
            work.enqueueUniqueWork(SWEEP, ExistingWorkPolicy.REPLACE, sweep)
        }
        widgets(c)
        Mirror.queue(c)
        TileService.requestListeningState(c, ComponentName(c, OtpTile::class.java))
        try {
            Ping.sync(c, again)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }
}
