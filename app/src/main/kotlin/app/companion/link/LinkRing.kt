package app.companion.link

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import app.companion.R

object LinkRing {
    private const val CHAN = "ring"
    private const val ID = 0x52494E47
    private const val MAX = 15_000L
    private val main = Handler(Looper.getMainLooper())
    private var tone: Ringtone? = null

    fun start(c: Context) {
        main.post {
            stop(c)
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHAN, "Find my phone", NotificationManager.IMPORTANCE_HIGH))
            val stop = PendingIntent.getBroadcast(c, ID, Intent(c, RingStop::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            nm.notify(
                ID,
                Notification.Builder(c, CHAN)
                    .setSmallIcon(R.drawable.ic_tile)
                    .setContentTitle("Your Mac is ringing this phone")
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setOngoing(true)
                    .setContentIntent(stop)
                    .addAction(Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_tile), "Stop", stop).build())
                    .build(),
            )
            tone = RingtoneManager.getRingtone(c, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))?.apply {
                audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                isLooping = true
                play()
            }
            main.postDelayed({ stop(c) }, MAX)
        }
    }

    fun stop(c: Context) {
        main.removeCallbacksAndMessages(null)
        tone?.stop()
        tone = null
        c.getSystemService(NotificationManager::class.java).cancel(ID)
    }
}

class RingStop : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = LinkRing.stop(c)
}
