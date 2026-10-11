package app.companion.link.feature

import android.app.Application
import android.media.AudioManager
import android.os.SystemClock
import app.companion.core.BulkFrame
import app.companion.core.BulkPipe
import app.companion.core.CallRestore
import app.companion.core.LinkMsg
import app.companion.link.LinkBad
import app.companion.link.LinkFeature
import app.companion.link.Obj
import app.companion.link.powers.Powers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallRelay(private val app: Application, private val powers: () -> Powers) : LinkFeature {
    override val key = "calls"
    override val cmds = emptySet<String>()

    private class Sess(val id: String) {
        @Volatile var pipe: BulkPipe? = null
        @Volatile var seen = 0L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var out: ((Obj) -> Unit)? = null
    private var cur: Sess? = null

    override fun start(sc: CoroutineScope, out: (Obj) -> Unit) {
        this.out = out
    }

    override fun stop() {
        val s = synchronized(this) { cur.also { cur = null } }
        s?.pipe?.let { runCatching(it::close) }
        out = null
    }

    fun register() {
        Calls.relay = ::toMac
        Calls.reclaim = ::toPhone
    }

    fun toMac(id: String) {
        val s = Sess(id)
        synchronized(this) {
            if (cur?.id == id) return
            cur?.pipe?.let { runCatching(it::close) }
            cur = s
        }
        scope.launch {
            if (!powers().bridge.up.value) return@launch fail(s, "powers")
            out?.invoke(LinkMsg.bulkWant("call", id))
            delay(WAIT)
            if (s.pipe == null) fail(s, "nowifi")
        }
    }

    fun toPhone(id: String) {
        val s = synchronized(this) { cur?.takeIf { it.id == id } } ?: return
        scope.launch { s.pipe?.let { runCatching(it::close) } ?: fail(s, null) }
    }

    suspend fun serve(p: BulkPipe) {
        val s = synchronized(this) { cur?.takeIf { it.id == p.ref && it.pipe == null }?.also { it.pipe = p } } ?: return
        val b = powers().bridge
        val am = app.getSystemService(AudioManager::class.java)
        val q = Channel<ByteArray>(QUEUE, BufferOverflow.DROP_OLDEST)
        var why: String? = null
        var job: Job? = null
        var dog: Job? = null
        val cb: (ByteArray) -> Unit = { q.trySend(it) }
        try {
            airborne({ b.cmd("call_audio", mapOf("on" to it)) }, CallRestore(am.getStreamVolume(VOICE), am.isMicrophoneMute), { v, m -> restore(am, v, m) }) {
                b.on[BulkFrame.AUDIO] = cb
                job = scope.launch {
                    try {
                        for (f in q) p.send(BulkFrame.AUDIO, f)
                    } catch (_: Exception) {
                        p.close()
                    }
                }
                s.seen = SystemClock.elapsedRealtime()
                dog = scope.launch {
                    while (true) {
                        val w = s.seen + LIVE - SystemClock.elapsedRealtime()
                        if (w <= 0) return@launch p.close()
                        delay(w)
                    }
                }
                out?.invoke(LinkMsg.callAudio("mac"))
                while (true) {
                    val (t, body) = p.read()
                    if (t != BulkFrame.AUDIO) continue
                    s.seen = SystemClock.elapsedRealtime()
                    b.stream(BulkFrame.AUDIO, body)
                }
            }
        } catch (e: LinkBad) {
            why = whyOf(e.message, b.up.value)
        } catch (_: Exception) {
        } finally {
            dog?.cancel()
            job?.cancel()
            q.close()
            b.on.remove(BulkFrame.AUDIO, cb)
            synchronized(this) { if (cur === s) cur = null }
            out?.invoke(LinkMsg.callAudio("phone", why))
        }
    }

    private fun fail(s: Sess, why: String?) {
        synchronized(this) { if (cur === s) cur = null else return }
        out?.invoke(LinkMsg.callAudio("phone", why))
    }

    private fun restore(am: AudioManager, v: Int, m: Boolean) {
        runCatching { am.setStreamVolume(VOICE, v, 0) }
        runCatching { am.isMicrophoneMute = m }
    }

    companion object {
        private const val WAIT = 12_000L
        private const val LIVE = 5_000L
        private const val QUEUE = 8
        private const val VOICE = AudioManager.STREAM_VOICE_CALL
        private val WHY = setOf("nowifi", "nointercept", "busy")

        internal fun whyOf(e: String?, up: Boolean) = if (e == "bridge") (if (up) "audio" else "powers") else e?.takeIf { it in WHY } ?: "audio"
    }
}

internal suspend fun airborne(cmd: suspend (Boolean) -> Obj, snap: CallRestore, restore: (Int, Boolean) -> Unit, run: suspend () -> Unit) {
    try {
        val r = cmd(true)
        if (r["ok"] != true) throw LinkBad(r["err"] as? String ?: "audio")
        run()
    } finally {
        withContext(NonCancellable) {
            if (runCatching { cmd(false) }.getOrNull()?.get("ok") != true) snap.take()?.let { (v, m) -> restore(v, m) }
        }
    }
}
