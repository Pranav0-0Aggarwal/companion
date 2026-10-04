package app.companion.system

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import androidx.glance.appwidget.updateAll
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.companion.sl
import app.companion.system.widget.OtpWidget
import java.util.concurrent.TimeUnit

object Live {
    const val OTP = "otp"
    const val BILLS = "bills"
    private const val SWEEP = "sweep"

    fun boot(app: Application) {
        app.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(OTP, "Codes", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(BILLS, "Bills", NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
        val daily = PeriodicWorkRequestBuilder<Reminders>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork("reminders", ExistingPeriodicWorkPolicy.KEEP, daily)
        val retain = PeriodicWorkRequestBuilder<Sweep>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork("retain", ExistingPeriodicWorkPolicy.KEEP, retain)
    }

    suspend fun refresh(c: Context) {
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
        OtpWidget().updateAll(c)
        TileService.requestListeningState(c, ComponentName(c, OtpTile::class.java))
    }
}
