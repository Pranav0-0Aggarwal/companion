package app.companion.system

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import app.companion.MainActivity
import app.companion.R
import app.companion.data.Item
import app.companion.ui.Voice
import app.companion.ui.codeText
import app.companion.ui.has

object Notes {
    const val OTP = 1
    const val CODE = "code"

    fun open(c: Context): PendingIntent =
        PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    fun otp(c: Context, i: Item, name: String) {
        val code = i.code ?: return
        val expires = i.expires ?: return
        if (!c.has(Manifest.permission.POST_NOTIFICATIONS)) return
        val icon = Icon.createWithResource(c, R.drawable.ic_tile)
        val copy = PendingIntent.getBroadcast(
            c, 0, Intent(c, CopyReceiver::class.java).putExtra(CODE, code),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val hidden = Notification.Builder(c, Live.OTP).setSmallIcon(R.drawable.ic_tile).setContentTitle("Code waiting").build()
        val n = Notification.Builder(c, Live.OTP)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(codeText(code))
            .setContentText(Voice.otp(name, i.title))
            .setContentIntent(open(c))
            .addAction(Notification.Action.Builder(icon, "Copy", copy).build())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(expires)
            .setTimeoutAfter(expires - System.currentTimeMillis())
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(hidden)
            .build()
        c.getSystemService(NotificationManager::class.java).notify(OTP, n)
    }
}
