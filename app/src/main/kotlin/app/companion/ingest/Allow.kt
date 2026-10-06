package app.companion.ingest

import android.app.Notification
import app.companion.core.Source

object Allow {
    const val GMAIL = "com.google.android.gm"

    private val chats = mapOf(
        "com.whatsapp" to Source.Wa,
        "com.whatsapp.w4b" to Source.Wa,
        "com.instagram.android" to Source.Ig,
        "com.instagram.lite" to Source.Ig,
    )

    private val skip = setOf(
        "android", "com.android.systemui", "com.android.vending", "com.google.android.gms",
        "com.google.android.dialer", "com.samsung.android.dialer", "com.samsung.android.incallui",
        "com.sec.android.daemonapp", "com.samsung.android.app.smartcapture",
    )

    private val status = setOf(
        Notification.CATEGORY_CALL, Notification.CATEGORY_PROGRESS, Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_SERVICE,
        Notification.CATEGORY_SYSTEM, Notification.CATEGORY_NAVIGATION, Notification.CATEGORY_STOPWATCH, Notification.CATEGORY_ALARM,
    )

    fun of(pkg: String, sms: String?) = chats[pkg] ?: Source.Notif.takeUnless { pkg in skip || pkg == sms }

    fun status(n: Notification) = n.category in status || n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_FOREGROUND_SERVICE) != 0
}
