package app.companion.link.feature

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import app.companion.core.ConvKey
import app.companion.core.LinkMsg
import app.companion.ingest.NotifService
import app.companion.link.LinkArgs
import app.companion.link.LinkBad
import app.companion.link.LinkFeature
import app.companion.link.Obj
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Messages : LinkFeature {
    override val key = "notifs"
    override val cmds = setOf("notif_reply", "notif_action", "notif_dismiss")

    override suspend fun run(t: String, q: Obj): Obj {
        val k = LinkArgs.text(q, "key", KEY)
        if (t == "notif_dismiss") {
            NotifService.live?.cancelNotification(k)
            return emptyMap()
        }
        val svc = NotifService.live ?: throw LinkBad("gone")
        val n = withContext(Dispatchers.IO) { runCatching { svc.activeNotifications }.getOrNull() }?.firstOrNull { it.key == k }?.notification ?: throw LinkBad("gone")
        try {
            if (t == "notif_reply") reply(svc, n, LinkArgs.text(q, "text", TEXT)) else act(n, q)
        } catch (_: PendingIntent.CanceledException) {
            throw LinkBad("gone")
        }
        return emptyMap()
    }

    fun msg(sbn: StatusBarNotification, name: String, title: String, text: String): Obj {
        val n = sbn.notification
        val x = n.extras
        val acts = n.actions.orEmpty()
        val last = x.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle::class.java)?.let(Notification.MessagingStyle.Message::getMessagesFromBundleArray)?.lastOrNull()
        val who = last?.senderPerson?.name?.toString()?.takeIf { it.isNotBlank() } ?: title.takeIf { it.isNotBlank() }
        val conv = x.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.takeIf { it.isNotBlank() }
        val acs = acts.withIndex().filter { (_, a) -> a.remoteInputs.isNullOrEmpty() && !a.title.isNullOrBlank() }.take(ACTIONS)
        return LinkMsg.notif(
            sbn.key, sbn.packageName, name, title, text, sbn.postTime, sbn.key,
            ConvKey.of(sbn.packageName, n.shortcutId, conv, title),
            acts.any { !it.remoteInputs.isNullOrEmpty() },
            acs.map { (i, a) -> mapOf("id" to i.toString(), "title" to a.title.toString()) },
            who?.let { mapOf("name" to it) },
        )
    }

    private fun reply(svc: NotifService, n: Notification, text: String) {
        val a = n.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() } ?: throw LinkBad("gone")
        val ins = a.remoteInputs
        val i = Intent()
        RemoteInput.addResultsToIntent(ins, i, Bundle().apply { putCharSequence(ins[0].resultKey, text) })
        a.actionIntent.send(svc, 0, i)
    }

    private fun act(n: Notification, q: Obj) {
        val a = (q["action"] as? String)?.toIntOrNull()?.let { n.actions?.getOrNull(it) } ?: throw LinkBad("gone")
        a.actionIntent.send()
    }

    private const val KEY = 512
    private const val TEXT = 2_000
    private const val ACTIONS = 3
}
