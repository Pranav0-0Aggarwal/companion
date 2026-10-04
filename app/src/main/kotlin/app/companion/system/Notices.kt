package app.companion.system

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import app.companion.MainActivity
import app.companion.R
import app.companion.data.Task
import app.companion.ui.Voice
import app.companion.ui.has

object Notices {
    private const val TASKS = "tasks"
    private const val NEXT = "next"
    private const val ALERT = 10_000
    private const val SOON = 20_000

    private fun nm(c: Context) = c.getSystemService(NotificationManager::class.java).also {
        it.createNotificationChannel(NotificationChannel(TASKS, "Reminders", NotificationManager.IMPORTANCE_HIGH))
        it.createNotificationChannel(NotificationChannel(NEXT, "Next reminder", NotificationManager.IMPORTANCE_LOW))
    }

    private fun open(c: Context) = PendingIntent.getActivity(
        c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun act(c: Context, label: String, id: Long, action: String) =
        Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_tile), label, Alarms.pi(c, id, action)).build()

    fun fire(c: Context, t: Task, name: String) {
        if (!c.has(Manifest.permission.POST_NOTIFICATIONS)) return
        val n = Notification.Builder(c, TASKS)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(t.title)
            .setContentText(Voice.addr(name, "reminder"))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open(c))
            .addAction(act(c, "Done", t.id, Remind.DONE))
            .addAction(act(c, "Snooze 1h", t.id, Remind.SNOOZE))
            .build()
        nm(c).notify(ALERT + t.id.toInt(), n)
    }

    fun near(c: Context, t: Task) {
        val at = t.remindAt ?: return
        if (!c.has(Manifest.permission.POST_NOTIFICATIONS)) return
        val n = Notification.Builder(c, NEXT)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(t.title)
            .setContentText("Next reminder")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(at)
            .setTimeoutAfter(at - System.currentTimeMillis())
            .setContentIntent(open(c))
            .addAction(act(c, "Done", t.id, Remind.DONE))
            .build()
        nm(c).notify(SOON + t.id.toInt(), n)
    }

    fun clear(c: Context, id: Long) {
        val m = c.getSystemService(NotificationManager::class.java)
        m.cancel(ALERT + id.toInt())
        m.cancel(SOON + id.toInt())
    }

    fun clearAlert(c: Context, id: Long) = c.getSystemService(NotificationManager::class.java).cancel(ALERT + id.toInt())
}
