package app.companion.ai

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.RemoteException
import app.companion.core.Needle
import app.companion.core.NeedleCalls
import app.companion.core.Plan
import app.companion.core.Planner
import app.companion.core.RulePlanner
import java.io.File
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking

object NeedleWire {
    const val LOAD = 1
    const val PLAN = 2
    const val REPLY = 3
    const val MODEL = "model"
    const val TEXT = "text"
    const val TOOLS = "tools"
    const val FACTS = "facts"
    const val JSON = "json"
    const val MB = "mb"
    const val ERROR = "error"
}

class NeedleService : Service() {
    private val thread = HandlerThread("needle").also { it.start() }
    private var handle = 0L
    private val messenger = Messenger(
        Handler(thread.looper) { m ->
            serve(m)
            true
        },
    )

    private fun serve(m: Message) {
        val to = m.replyTo ?: return
        val out = Bundle()
        try {
            val d = m.data
            when (m.what) {
                NeedleWire.LOAD -> {
                    if (handle != 0L) NeedleJni.free(handle)
                    handle = d.getParcelable(NeedleWire.MODEL, ParcelFileDescriptor::class.java)?.use { NeedleJni.load(it.fd) } ?: 0
                    check(handle != 0L)
                    out.putLong(NeedleWire.MB, Debug.getPss() / 1024)
                }
                NeedleWire.PLAN -> out.putString(NeedleWire.JSON, NeedleJni.run(handle, d.getString(NeedleWire.TEXT).orEmpty(), d.getString(NeedleWire.TOOLS).orEmpty(), d.getString(NeedleWire.FACTS).orEmpty()))
            }
        } catch (_: Throwable) {
            out.putString(NeedleWire.ERROR, "failed")
        }
        try {
            to.send(Message.obtain(null, NeedleWire.REPLY).apply { data = out })
        } catch (_: RemoteException) {
        }
    }

    override fun onBind(intent: Intent): IBinder = messenger.binder

    override fun onDestroy() {
        Process.killProcess(Process.myPid())
    }
}

private class Link : ServiceConnection {
    val ready = CountDownLatch(1)

    @Volatile var binder: IBinder? = null

    @Volatile var dead = false

    override fun onServiceConnected(n: ComponentName, b: IBinder) {
        binder = b
        ready.countDown()
    }

    override fun onServiceDisconnected(n: ComponentName) {
        dead = true
    }

    override fun onBindingDied(n: ComponentName) {
        dead = true
    }
}

class NeedleClient private constructor(private val c: Context, private val link: Link, private val to: Messenger, override val mb: Long) : Runner {
    override val accel = "CPU"
    override val alive get() = !link.dead

    fun plan(text: String, system: String): String? {
        val r = try {
            ask(to, NeedleWire.PLAN, Bundle().apply {
                putString(NeedleWire.TEXT, text.take(MAX_TEXT))
                putString(NeedleWire.TOOLS, Needle.TOOLS)
                putString(NeedleWire.FACTS, system)
            }, RUN_SECS)
        } catch (_: RemoteException) {
            link.dead = true
            null
        }
        return r?.getString(NeedleWire.JSON)
    }

    override fun close() = c.unbindService(link)

    companion object {
        private const val MAX_TEXT = 200
        private const val BIND_SECS = 5L
        private const val LOAD_SECS = 20L
        private const val RUN_SECS = 15L

        private fun ask(to: Messenger, what: Int, data: Bundle, secs: Long): Bundle? {
            val got = AtomicReference<Bundle?>()
            val latch = CountDownLatch(1)
            val back = Messenger(
                Handler(Looper.getMainLooper()) { m ->
                    got.set(m.data)
                    latch.countDown()
                    true
                },
            )
            to.send(Message.obtain(null, what).apply { replyTo = back; this.data = data })
            return if (latch.await(secs, TimeUnit.SECONDS)) got.get() else null
        }

        fun open(c: Context, model: File): NeedleClient {
            val link = Link()
            check(c.bindService(Intent(c, NeedleService::class.java), link, Context.BIND_AUTO_CREATE))
            try {
                check(link.ready.await(BIND_SECS, TimeUnit.SECONDS))
                val to = Messenger(link.binder)
                val r = ParcelFileDescriptor.open(model, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    ask(to, NeedleWire.LOAD, Bundle().apply { putParcelable(NeedleWire.MODEL, fd) }, LOAD_SECS)
                }
                check(r != null && r.getString(NeedleWire.ERROR) == null)
                return NeedleClient(c, link, to, r.getLong(NeedleWire.MB))
            } catch (e: Throwable) {
                c.unbindService(link)
                throw e
            }
        }
    }
}

class NeedlePlanner(private val c: Context, private val gov: Governor, private val rules: Planner = RulePlanner()) : Planner {
    private val zone = ZoneId.of("Asia/Kolkata")

    override fun plan(text: String, now: Long): Plan {
        val base = rules.plan(text, now)
        if (base.explicit && base.queries.isNotEmpty() || text.trim().split(Regex("\\s+")).size < MIN_WORDS) return base
        val model = Models.file(c, Manifest.needle) ?: return base
        val json = try {
            runBlocking { gov.run(Manifest.needle, { NeedleClient.open(c, model) }) { it.plan(text, Needle.system(now, zone)) } }
        } catch (_: LowMemory) {
            return base
        } catch (_: Exception) {
            null
        }
        val qs = json?.let { NeedleCalls(now, zone).parse(it) }.orEmpty()
        return if (qs.isEmpty()) Plan(emptyList(), false, true) else Plan(qs, true)
    }

    private companion object {
        const val MIN_WORDS = 3
    }
}
