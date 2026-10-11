package app.companion.link.feature

import android.Manifest
import android.app.Application
import android.app.Notification
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.telecom.Call
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.telephony.SmsManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import app.companion.core.CallInfo
import app.companion.core.LinkCrypto
import app.companion.core.LinkMsg
import app.companion.link.LinkArgs
import app.companion.link.LinkBad
import app.companion.link.LinkFeature
import app.companion.link.Obj
import app.companion.link.powers.Powers
import app.companion.ui.has
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

class Calls(private val app: Application, private val powers: Powers) : LinkFeature {
    override val key = "calls"
    override val cmds = setOf("call_answer", "call_end", "call_reject", "call_dial", "call_mute", "call_hold", "call_dtmf", "call_move")

    private data class Fb(val id: String, val state: String, val started: Long?)
    private data class Note(val name: String?, val number: String?, val photo: String?)

    private val tm = app.getSystemService(TelecomManager::class.java)
    private val tel = app.getSystemService(TelephonyManager::class.java)
    private val listener = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) = phone(state)
    }
    private var out: ((Obj) -> Unit)? = null
    private var fb: Fb? = null
    private var note: Note? = null
    private var sent: Obj? = null
    private var wantMac = false

    override fun start(sc: CoroutineScope, out: (Obj) -> Unit) {
        stop()
        synchronized(this) { this.out = out }
        live = this
        if (app.has(Manifest.permission.READ_PHONE_STATE)) runCatching { tel.registerTelephonyCallback(app.mainExecutor, listener) }
        report()
    }

    override fun stop() {
        runCatching { tel.unregisterTelephonyCallback(listener) }
        if (live === this) live = null
        synchronized(this) {
            out = null
            fb = null
            note = null
            sent = null
            wantMac = false
        }
    }

    override suspend fun run(t: String, q: Obj): Obj {
        val full = MacInCall.live != null
        val c = MacInCall.current()
        val mac = q["where"] == "mac"
        when (t) {
            "call_answer" -> {
                if (full) (c ?: gone()).answer(VideoProfile.STATE_AUDIO_ONLY) else ans { acceptRingingCall() }
                want(mac)
            }
            "call_end" -> if (full) (c ?: gone()).disconnect() else ans { endCall() }
            "call_reject" -> reject(full, c, q["text"] as? String)
            "call_dial" -> {
                dial(LinkArgs.text(q, "number", 32))
                want(mac)
            }
            "call_mute" -> MacInCall.live?.setMuted(on(q)) ?: throw LinkBad("unsupported")
            "call_hold" -> if (full) (c ?: gone()).let { if (on(q)) it.hold() else it.unhold() } else throw LinkBad("unsupported")
            "call_dtmf" -> if (full) tone(c ?: gone(), q) else throw LinkBad("unsupported")
            "call_move" -> move(mac)
        }
        return emptyMap()
    }

    fun fromNotification(sbn: StatusBarNotification) {
        val n = sbn.notification
        if (out == null || n.category != Notification.CATEGORY_CALL) return
        val x = n.extras
        val title = x.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = x.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val num = CallInfo.number(text) ?: CallInfo.number(title)
        val photo = note?.photo ?: n.getLargeIcon()?.let { icon -> runCatching { jpeg(icon.loadDrawable(app)) }.getOrNull() }
        synchronized(this) {
            note = Note(title.takeIf { it.isNotBlank() && CallInfo.number(it) == null }, num, photo)
            if (fb?.state == "dialing" && x.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)) fb = fb?.copy(state = "active", started = System.currentTimeMillis())
        }
        report()
    }

    @Synchronized
    fun report(drop: Call? = null) {
        val o = out ?: return
        val c = cur(drop)
        if (c == null) {
            val id = sent?.get("id") as? String ?: return
            o(LinkMsg.call(id, "ended", null, null, null, null, ctl()))
            sent = null
            note = null
            fb = null
            wantMac = false
            return
        }
        if (c == sent) return
        sent = c
        o(c)
        if (c["state"] == "active" && wantMac) {
            wantMac = false
            relay?.invoke(c["id"] as String)
        }
    }

    @Synchronized
    private fun phone(s: Int) {
        if (MacInCall.live != null) return
        val now = System.currentTimeMillis()
        fb = when (s) {
            TelephonyManager.CALL_STATE_RINGING -> Fb("c$now", "ringing", null)
            TelephonyManager.CALL_STATE_OFFHOOK -> fb?.let { if (it.state == "ringing") it.copy(state = "active", started = now) else it } ?: Fb("c$now", "dialing", null)
            else -> null
        }
        report()
    }

    private fun cur(drop: Call?): Obj? {
        val full = MacInCall.live != null
        val n = note
        if (!full) return fb?.let { LinkMsg.call(it.id, it.state, n?.number, n?.name, n?.photo, it.started, ctl()) }
        val c = MacInCall.current(drop) ?: return null
        val d = c.details
        val st = when (c.state) {
            Call.STATE_RINGING -> "ringing"
            Call.STATE_ACTIVE -> "active"
            Call.STATE_HOLDING -> "held"
            else -> "dialing"
        }
        val at = if (st == "active" || st == "held") d.connectTimeMillis.takeIf { it > 0 } ?: System.currentTimeMillis() else null
        return LinkMsg.call("c${d.creationTimeMillis}", st, d.handle?.schemeSpecificPart ?: n?.number, d.contactDisplayName?.toString() ?: n?.name, n?.photo, at, ctl())
    }

    private fun ctl() = if (MacInCall.live != null) "full" else "basic"

    private fun want(mac: Boolean) {
        synchronized(this) { wantMac = mac }
    }

    private fun gone(): Nothing = throw LinkBad("gone")

    private fun on(q: Obj) = q["on"] as? Boolean ?: throw LinkBad("on")

    private fun perm(p: String, name: String) {
        if (!app.has(p)) throw LinkBad("perm:$name")
    }

    private fun ans(f: TelecomManager.() -> Unit) {
        perm(Manifest.permission.ANSWER_PHONE_CALLS, "phone")
        tm.f()
    }

    private fun reject(full: Boolean, c: Call?, text: String?) {
        val to = note?.number ?: c?.details?.handle?.schemeSpecificPart
        if (full) return (c ?: gone()).reject(text != null, text)
        ans { endCall() }
        if (text == null || to == null) return
        perm(Manifest.permission.SEND_SMS, "sms")
        app.getSystemService(SmsManager::class.java).sendTextMessage(to, null, text.take(SMS), null, null)
    }

    private fun dial(n: String) {
        if (!n.matches(DIAL)) throw LinkBad("number")
        perm(Manifest.permission.CALL_PHONE, "phone")
        tm.placeCall(Uri.fromParts("tel", n, null), Bundle())
    }

    private suspend fun tone(c: Call, q: Obj) {
        val d = (q["digit"] as? String)?.singleOrNull()?.takeIf { it in KEYS } ?: throw LinkBad("digit")
        c.playDtmfTone(d)
        delay(TONE)
        c.stopDtmfTone()
    }

    private fun move(mac: Boolean) {
        val id = synchronized(this) { sent?.get("id") as? String } ?: gone()
        (if (mac) relay else reclaim)?.invoke(id)
    }

    private fun jpeg(d: Drawable?): String? {
        val b = (d as? BitmapDrawable)?.bitmap ?: return null
        val s = SIDE.toFloat() / maxOf(b.width, b.height)
        val t = if (s < 1) Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true) else b
        return generateSequence(80) { it - 15 }.takeWhile { it > 20 }.firstNotNullOfOrNull { q ->
            ByteArrayOutputStream().also { t.compress(Bitmap.CompressFormat.JPEG, q, it) }.toByteArray().takeIf { it.size <= RAW }
        }?.let(LinkCrypto::b64)
    }

    companion object {
        @Volatile var live: Calls? = null
        @Volatile var relay: ((String) -> Unit)? = null
        @Volatile var reclaim: ((String) -> Unit)? = null
        private val DIAL = Regex("""\+?[0-9 ()*#-]{3,32}""")
        private const val KEYS = "0123456789*#"
        private const val TONE = 150L
        private const val SIDE = 96
        private const val RAW = 12 * 1024
        private const val SMS = 160
    }
}
