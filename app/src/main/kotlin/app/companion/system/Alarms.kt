package app.companion.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.companion.data.Task
import app.companion.sl

object Alarms {
    private const val HOUR = 3_600_000L

    fun pi(c: Context, id: Long, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            c,
            (id * 4 + Remind.codes.getValue(action)).toInt(),
            Intent(c, Remind::class.java).setAction(action).putExtra(Remind.ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun set(c: Context, t: Task) {
        cancel(c, t.id)
        val at = t.remindAt ?: return
        val now = System.currentTimeMillis()
        if (t.done || t.gone || at <= now) return
        val am = c.getSystemService(AlarmManager::class.java)
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c, t.id, Remind.FIRE))
        if (at - HOUR > now) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at - HOUR, pi(c, t.id, Remind.NEAR))
        } else {
            Notices.near(c, t)
        }
    }

    fun cancel(c: Context, id: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        am.cancel(pi(c, id, Remind.FIRE))
        am.cancel(pi(c, id, Remind.NEAR))
        Notices.clear(c, id)
    }

    suspend fun all(c: Context) = c.sl.repo.pending().forEach { set(c, it) }
}
