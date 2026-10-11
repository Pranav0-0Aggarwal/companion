package app.companion.link.feature

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import app.companion.R
import app.companion.core.HandoffUrl
import app.companion.core.LinkMsg
import app.companion.link.LinkBad
import app.companion.link.LinkFeature
import app.companion.link.Obj
import app.companion.sl
import app.companion.ui.has
import kotlinx.coroutines.CoroutineScope

class Handoff(private val app: Application) : LinkFeature {
    override val key = "handoff"
    override val cmds = setOf("handoff")

    override suspend fun run(t: String, q: Obj): Obj {
        val url = q["url"] as? String ?: throw LinkBad("url")
        if (!HandoffUrl.openable(url)) throw LinkBad("url")
        if (!app.has(Manifest.permission.POST_NOTIFICATIONS)) throw LinkBad("perm:notifications")
        val title = (q["title"] as? String)?.take(TITLE)?.takeIf { it.isNotBlank() } ?: url.take(TITLE)
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(key, "Open on phone", NotificationManager.IMPORTANCE_HIGH))
        nm.notify(
            key.hashCode(),
            Notification.Builder(app, key)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentTitle("Open on phone: $title")
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(app, 0, Intent(Intent.ACTION_VIEW, Uri.parse(url).normalizeScheme()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE))
                .build(),
        )
        return emptyMap()
    }

    companion object {
        private const val TITLE = 120

        fun send(c: Context, url: String, title: String?) {
            if (Files.ready(c, "handoff")) c.sl.link.push(LinkMsg.handoff(url, title?.take(TITLE).orEmpty()))
        }
    }
}

class Dnd(private val app: Application) : LinkFeature {
    override val key = "dnd"
    override val cmds = setOf("dnd")
    private val nm = app.getSystemService(NotificationManager::class.java)
    private var rx: BroadcastReceiver? = null

    override fun start(sc: CoroutineScope, out: (Obj) -> Unit) {
        stop()
        fun report() = out(LinkMsg.dnd(nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL))
        rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = report()
        }.also { app.registerReceiver(it, IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED), Context.RECEIVER_NOT_EXPORTED) }
        report()
    }

    override fun stop() {
        rx?.let { runCatching { app.unregisterReceiver(it) } }
        rx = null
    }

    override suspend fun run(t: String, q: Obj): Obj {
        val on = q["on"] as? Boolean ?: throw LinkBad("on")
        if (!nm.isNotificationPolicyAccessGranted) throw LinkBad("perm:dnd")
        nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
        return emptyMap()
    }
}
