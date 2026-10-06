package app.companion.system

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import app.companion.CoverActivity
import app.companion.MainActivity
import app.companion.R
import app.companion.data.Item
import app.companion.ui.Voice
import app.companion.ui.codeText
import app.companion.ui.has

object Notes {
    const val OTP = 1
    const val CODE = "code"
    const val DROP = "drop"
    private const val COPY = 0x434F5059
    private var shown = 0L

    fun open(c: Context): PendingIntent =
        PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    fun to(c: Context, route: String, item: Long = 0): PendingIntent =
        PendingIntent.getActivity(
            c, (route.hashCode() * 31L + item).toInt(), intent(c, route, item).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun intent(c: Context, route: String, item: Long = 0): Intent =
        Intent(c, MainActivity::class.java).setAction(MainActivity.OPEN).putExtra(MainActivity.ROUTE, route).putExtra(MainActivity.ITEM, item)

    fun copy(c: Context, code: String): PendingIntent = PendingIntent.getBroadcast(
        c, code.hashCode() xor COPY, Intent(c, CopyReceiver::class.java).putExtra(CODE, code),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun drop(c: Context, id: Long): PendingIntent = PendingIntent.getBroadcast(
        c, id.toInt() xor COPY.inv(), Intent(c, CopyReceiver::class.java).putExtra(DROP, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun otp(c: Context, i: Item, name: String) {
        val code = i.code ?: return
        val expires = i.expires ?: return
        if (!c.has(Manifest.permission.POST_NOTIFICATIONS)) return
        val icon = Icon.createWithResource(c, R.drawable.ic_tile)
        val nm = c.getSystemService(NotificationManager::class.java)
        if (i.id != shown) nm.cancel(OTP)
        shown = i.id
        val hidden = Notification.Builder(c, Live.OTP).setSmallIcon(R.drawable.ic_tile).setContentTitle("Code waiting").build()
        val n = Notification.Builder(c, Live.OTP)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(codeText(code))
            .setContentText(Voice.otp(name, i.title))
            .setContentIntent(open(c))
            .addAction(Notification.Action.Builder(icon, "Copy", copy(c, code)).build())
            .addAction(Notification.Action.Builder(icon, "Dismiss", drop(c, i.id)).build())
            .setDeleteIntent(drop(c, i.id))
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(expires)
            .setTimeoutAfter(expires - System.currentTimeMillis())
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(hidden)
            .apply { if (nm.canUseFullScreenIntent()) setFullScreenIntent(PendingIntent.getActivity(c, OTP, CoverActivity.intent(c, 0), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT), true) }
            .build()
        nm.notify(OTP, n)
    }
}
