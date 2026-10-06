package app.companion.ingest

import android.app.Notification
import android.content.ComponentName
import android.os.Bundle
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.companion.core.Chats
import app.companion.core.Raw
import app.companion.core.Source
import app.companion.sl
import app.companion.system.Notes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class NotifService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        count()
        take(sbn, true)
    }

    private fun take(sbn: StatusBarNotification, live: Boolean) {
        val n = sbn.notification
        if (sbn.packageName == packageName || Allow.status(n)) return
        val src = Allow.of(sbn.packageName, sms) ?: return
        val x = n.extras
        val title = x.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val chat = Chats.chat(src)
        val items = (if (chat) messages(src, x, title) else emptyList()).ifEmpty {
            val text = (x.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: x.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
            listOf(if (chat) Raw(src, title, "", text, sbn.postTime) else Raw(src, sbn.packageName, title, text, sbn.postTime))
        }
        val ingest = sl.ingest
        scope.launch { items.forEach { ingest.handle(it, "${sbn.key}|${if (chat) it.at else it.title + it.body}", live = live) } }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = count()

    override fun onListenerConnected() {
        up.value = true
        live = this
        count()
    }

    private val sms by lazy { Telephony.Sms.getDefaultSmsPackage(this) }

    private fun apps() = runCatching {
        activeNotifications.filter { it.isClearable && it.packageName != packageName && (it.packageName == sms || Allow.of(it.packageName, sms) == Source.Notif) }
    }.getOrDefault(emptyList())

    private fun count() {
        shade.value = apps().size
    }

    override fun onListenerDisconnected() {
        up.value = false
        live = null
        requestRebind(ComponentName(this, NotifService::class.java))
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

    companion object {
        val up = MutableStateFlow<Boolean?>(null)
        val shade = MutableStateFlow(0)
        @Volatile private var live: NotifService? = null

        fun clearApps() {
            val s = live ?: return
            runCatching {
                val all = s.apps()
                all.forEach { s.take(it, false) }
                val own = s.activeNotifications.filter { it.packageName == s.packageName && it.isClearable && it.id != Notes.OTP }
                s.cancelNotifications((all + own).map { it.key }.toTypedArray())
            }
        }
    }
}
