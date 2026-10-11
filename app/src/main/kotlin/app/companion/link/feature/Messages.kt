package app.companion.link.feature

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
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
import app.companion.link.LinkStore
import app.companion.link.Obj
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Messages : LinkFeature {
    override val key = "notifs"
    override val cmds = setOf("notif_reply", "notif_action", "notif_dismiss")

    override suspend fun run(t: String, q: Obj): Obj {
        val svc = NotifService.live ?: throw LinkBad("gone")
        val k = LinkArgs.text(q, "key", KEY)
        val sbn = withContext(Dispatchers.IO) { runCatching { svc.activeNotifications }.getOrNull() }?.firstOrNull { it.key == k && it.packageName in LinkStore.apps(svc) } ?: throw LinkBad("gone")
        try {
            when (t) {
                "notif_reply" -> reply(svc, sbn.notification, LinkArgs.text(q, "text", TEXT))
                "notif_action" -> act(sbn.notification, q)
                else -> svc.cancelNotification(k)
            }
        } catch (_: PendingIntent.CanceledException) {
            throw LinkBad("gone")
        }
        return emptyMap()
    }

    fun msg(sbn: StatusBarNotification, name: String, title: String, text: String): Obj? {
        val n = sbn.notification
        val x = n.extras
        val msgs = x.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle::class.java)?.let(Notification.MessagingStyle.Message::getMessagesFromBundleArray)
        val last = msgs?.lastOrNull()
        val me = x.getParcelable(Notification.EXTRA_MESSAGING_PERSON, Person::class.java)
        if (last != null && own(last.senderPerson, me)) return null
        val who = last?.senderPerson?.name?.toString()?.takeIf { it.isNotBlank() } ?: title.takeIf { it.isNotBlank() }
        val conv = x.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.takeIf { it.isNotBlank() }
        return LinkMsg.notif(
            sbn.key, sbn.packageName, name, title, text, last?.timestamp?.takeIf { it > 0 } ?: sbn.postTime, sbn.key,
            ConvKey.of(sbn.packageName, n.shortcutId, conv, title),
            replier(n) != null,
            extras(n).map { (i, a) -> mapOf("id" to i.toString(), "title" to a.title.toString()) },
            who?.let { mapOf("name" to it) },
        )
    }

    private fun own(p: Person?, me: Person?) = p == null || me != null && (p.key?.let { it == me.key } ?: (p.name == me.name))

    private fun free(a: Notification.Action) = a.remoteInputs?.any { it.allowFreeFormInput } == true

    private fun replier(n: Notification) = n.actions.orEmpty().let { all -> all.firstOrNull { it.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY && free(it) } ?: all.firstOrNull(::free) }

    private fun extras(n: Notification) = n.actions.orEmpty().withIndex().filter { (_, a) -> a.remoteInputs.isNullOrEmpty() && !a.title.isNullOrBlank() }.take(ACTIONS)

    private fun reply(svc: NotifService, n: Notification, text: String) {
        val a = replier(n) ?: throw LinkBad("gone")
        val ri = a.remoteInputs.first { it.allowFreeFormInput }
        val i = Intent()
        RemoteInput.addResultsToIntent(arrayOf(ri), i, Bundle().apply { putCharSequence(ri.resultKey, text) })
        RemoteInput.setResultsSource(i, RemoteInput.SOURCE_FREE_FORM_INPUT)
        (a.actionIntent ?: throw LinkBad("gone")).send(svc, 0, i)
    }

    private fun act(n: Notification, q: Obj) {
        val i = (q["action"] as? String)?.toIntOrNull()
        val a = extras(n).firstOrNull { it.index == i }?.value ?: throw LinkBad("gone")
        (a.actionIntent ?: throw LinkBad("gone")).send()
    }

    private const val KEY = 512
    private const val TEXT = 2_000
    private const val ACTIONS = 3
}
