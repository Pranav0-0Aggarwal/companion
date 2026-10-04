package app.companion.ingest

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.companion.core.Raw
import app.companion.core.Source
import app.companion.sl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotifService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val src = Allow.of(sbn.packageName) ?: return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val x = n.extras
        val title = x.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val chat = src == Source.Wa || src == Source.Ig
        val items = (if (chat) messages(src, x, title) else emptyList()).ifEmpty {
            val text = (x.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: x.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
            listOf(if (chat) Raw(src, title, "", text, sbn.postTime) else Raw(src, sbn.packageName, title, text, sbn.postTime))
        }
        val ingest = sl.ingest
        scope.launch { items.forEach { ingest.handle(it, "${sbn.key}|${it.at}") } }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun messages(src: Source, x: Bundle, title: String): List<Raw> {
        val group = x.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString().orEmpty()
        val raw = x.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle::class.java) ?: return emptyList()
        return Notification.MessagingStyle.Message.getMessagesFromBundleArray(raw).mapNotNull { m ->
            val text = m.text?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Raw(src, m.senderPerson?.name?.toString() ?: title, group, text, m.timestamp)
        }
    }
}
