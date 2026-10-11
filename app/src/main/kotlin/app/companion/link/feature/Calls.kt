package app.companion.link.feature

import android.Manifest
import android.annotation.SuppressLint
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
import app.companion.link.LinkBad
import app.companion.link.LinkFeature
import app.companion.link.Obj
import app.companion.link.powers.Powers
import app.companion.ui.has
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

class Calls(private val app: Application, private val powers: () -> Powers) : LinkFeature {
    override val key = "calls"
    override val cmds = setOf("call_answer", "call_end", "call_reject", "call_dial", "call_mute", "call_hold", "call_dtmf", "call_move")

    private data class Fb(val id: String, val state: String, val started: Long?, val since: Long)
    private data class Note(val id: String?, val name: String?, val number: String?, val photo: String?, val at: Long)

    private val tm = app.getSystemService(TelecomManager::class.java)
    private val tel = app.getSystemService(TelephonyManager::class.java)
    private val listener = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) = phone(state)
    }
    @Volatile private var out: ((Obj) -> Unit)? = null
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
            val id = sent?.get("id") as? String
            if (id != null) out?.invoke(LinkMsg.call(id, "ended", null, null, null, null, ctl()))
            out = null
            fb = null
            note = null
            sent = null
            wantMac = false
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun run(t: String, q: Obj): Obj {
        val full = MacInCall.live != null
        val c = MacInCall.current()
        val mac = q["where"] == "mac"
        when (t) {
            "call_answer" -> arm(mac) { if (full) (c ?: gone()).answer(VideoProfile.STATE_AUDIO_ONLY) else ans { acceptRingingCall() } }
            "call_end" -> if (full) (c ?: gone()).disconnect() else ans { endCall() }
            "call_reject" -> reject(full, c, (q["text"] as? String)?.trim()?.take(SMS)?.takeIf { it.isNotEmpty() })
            "call_dial" -> arm(mac) { dial(q["number"] as? String ?: throw LinkBad("number")) }
            "call_mute" -> MacInCall.live?.setMuted(on(q)) ?: throw LinkBad("unsupported")
            "call_hold" -> if (full) (c ?: gone()).let { if (on(q)) it.hold() else it.unhold() } else throw LinkBad("unsupported")
            "call_dtmf" -> if (full) tone(c ?: gone(), q) else throw LinkBad("unsupported")
            "call_move" -> move(mac)
        }
        return emptyMap()
    }

    fun fromNotification(sbn: StatusBarNotification) {
        val n = sbn.notification
        if (out == null || n.category != Notification.CATEGORY_CALL || sbn.packageName != tm.defaultDialerPackage) return
        val x = n.extras
        val title = x.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = x.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val num = CallInfo.number(text) ?: CallInfo.number(title)
        val id = if (MacInCall.live != null) MacInCall.current()?.let { "c${it.details.creationTimeMillis}" } else synchronized(this) { fb?.id }
        val photo = n.getLargeIcon()?.let { icon -> runCatching { jpeg(icon.loadDrawable(app)) }.getOrNull() }
        synchronized(this) {
            note = Note(id, title.takeIf { it.isNotBlank() && CallInfo.number(it) == null }, num, photo, System.currentTimeMillis())
            if (fb?.state == "dialing" && x.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)) fb = fb?.copy(state = "active", started = System.currentTimeMillis())
        }
        report()
    }

    @Synchronized
    fun busy() = sent != null

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
            TelephonyManager.CALL_STATE_RINGING -> fresh(now, "ringing")
            TelephonyManager.CALL_STATE_OFFHOOK -> fb?.let { if (it.state == "ringing") it.copy(state = "active", started = now) else it } ?: fresh(now, "dialing")
            else -> null
        }
        report()
    }

    private fun fresh(now: Long, state: String): Fb {
        return Fb("c$now", state, null, now)
    }

    private fun cur(drop: Call?): Obj? {
        val full = MacInCall.live != null
        if (!full) return fb?.let { f ->
            val n = note?.takeIf { CallInfo.mine(it.id, it.at, f.id, f.since - GRACE) }
            LinkMsg.call(f.id, f.state, n?.number, n?.name, n?.photo, f.started, ctl())
        }
        val c = MacInCall.current(drop) ?: return null
        val d = c.details
        val n = note?.takeIf { CallInfo.mine(it.id, it.at, "c${d.creationTimeMillis}", d.creationTimeMillis) }
        val st = MacInCall.name(c.state)
        val at = if (st == "active" || st == "held") d.connectTimeMillis.takeIf { it > 0 } ?: System.currentTimeMillis() else null
        return LinkMsg.call("c${d.creationTimeMillis}", st, d.handle?.schemeSpecificPart ?: n?.number, d.contactDisplayName?.toString() ?: n?.name, n?.photo, at, ctl())
    }

    private fun ctl() = if (MacInCall.live != null) "full" else "basic"

    private fun arm(mac: Boolean, f: () -> Unit) {
        synchronized(this) { wantMac = mac }
        try {
            f()
        } catch (e: Exception) {
            synchronized(this) { wantMac = false }
            throw e
        }
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

    @SuppressLint("MissingPermission")
    private fun reject(full: Boolean, c: Call?, text: String?) {
        if (full) return (c ?: gone()).reject(text != null, text)
        val to = synchronized(this) { note?.number }
        ans { endCall() }
        if (text == null) return
        if (to == null) throw LinkBad("number")
        perm(Manifest.permission.SEND_SMS, "sms")
        app.getSystemService(SmsManager::class.java).sendTextMessage(to, null, text, null, null)
    }

    @SuppressLint("MissingPermission")
    private fun dial(n: String) {
        val t = n.trim()
        if (!CallInfo.dialable(t)) throw LinkBad("number")
        perm(Manifest.permission.CALL_PHONE, "phone")
        tm.placeCall(Uri.fromParts("tel", t, null), Bundle())
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
        private const val KEYS = "0123456789*#"
        private const val TONE = 150L
        private const val SIDE = 96
        private const val RAW = 12 * 1024
        private const val SMS = 160
        private const val GRACE = 3_000L
    }
}
