package app.companion.ai

import android.app.Service
import android.content.Context
import android.content.Intent
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
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Predicate

object ChatWire {
    const val LOAD = 1
    const val RUN = 2
    const val REPLY = 3
    const val PIECE = 4
    const val CANCEL = 5
    const val WARM = 6
    const val MODEL = "model"
    const val THREADS = "threads"
    const val PROMPT = "prompt"
    const val PIN = "pin"
    const val PRE = "pre"
    const val GRAMMAR = "grammar"
    const val MAX = "max"
    const val TEMP = "temp"
    const val PRIO = "prio"
    const val TEXT = "text"
    const val OK = "ok"
    const val MB = "mb"
    const val ERROR = "error"
}

class ChatService : Service() {
    private val work = Executors.newSingleThreadExecutor()

    @Volatile private var handle = 0L

    private val messenger = Messenger(
        Handler(Looper.getMainLooper()) { m ->
            val to = m.replyTo
            if (m.what == ChatWire.CANCEL) {
                if (handle != 0L) ChatJni.cancel(handle)
            } else if (to != null) {
                val what = m.what
                val data = Bundle(m.data)
                work.execute { serve(what, data, to) }
            }
            true
        },
    )

    private fun send(to: Messenger, what: Int, data: Bundle) = try {
        to.send(Message.obtain(null, what).apply { this.data = data })
        true
    } catch (_: RemoteException) {
        false
    }

    private fun rssMb() = runCatching { File("/proc/self/statm").readText().split(' ')[1].toLong() * Os.sysconf(OsConstants._SC_PAGESIZE) shr 20 }.getOrDefault(-1)

    private fun log() {
        val s = ChatJni.stats(handle)
        Log.i("Companion", "chat prefill=${s[0]}/${s[1]}ms gen=${s[2]}/${s[3]}ms rss=${rssMb()}")
    }

    private fun serve(what: Int, d: Bundle, to: Messenger) {
        val out = Bundle()
        try {
            when (what) {
                ChatWire.LOAD -> {
                    if (handle != 0L) ChatJni.free(handle)
                    handle = 0
                    handle = d.getParcelable(ChatWire.MODEL, ParcelFileDescriptor::class.java)?.use { ChatJni.load(it.fd, d.getInt(ChatWire.THREADS, 2)) } ?: 0
                    check(handle != 0L)
                    out.putLong(ChatWire.MB, Debug.getPss() / 1024)
                }
                ChatWire.RUN -> {
                    Process.setThreadPriority(d.getInt(ChatWire.PRIO, Process.THREAD_PRIORITY_DEFAULT))
                    val ok = ChatJni.run(
                        handle, d.getString(ChatWire.PROMPT).orEmpty(), d.getString(ChatWire.PIN).orEmpty(), d.getString(ChatWire.GRAMMAR).orEmpty(), d.getInt(ChatWire.MAX, 256),
                        d.getInt(ChatWire.THREADS, 2), d.getInt(ChatWire.PRE, 2), d.getFloat(ChatWire.TEMP, 0f),
                        Predicate { s -> send(to, ChatWire.PIECE, Bundle().apply { putString(ChatWire.TEXT, s) }) },
                    )
                    log()
                    out.putBoolean(ChatWire.OK, ok)
                }
                ChatWire.WARM -> {
                    Process.setThreadPriority(d.getInt(ChatWire.PRIO, Process.THREAD_PRIORITY_DEFAULT))
                    val ok = ChatJni.warm(handle, d.getString(ChatWire.PIN).orEmpty(), d.getInt(ChatWire.PRE, 2))
                    log()
                    out.putBoolean(ChatWire.OK, ok)
                }
            }
        } catch (_: Throwable) {
            out.putString(ChatWire.ERROR, "failed")
        }
        send(to, ChatWire.REPLY, out)
    }

    override fun onBind(intent: Intent): IBinder = messenger.binder

    override fun onDestroy() {
        Process.killProcess(Process.myPid())
    }
}

class ChatClient private constructor(private val c: Context, private val link: Link, private val to: Messenger, override val mb: Long) : Runner {
    override val accel = "CPU"
    override val alive get() = !link.dead

    private val rx = HandlerThread("chat-rx").also { it.start() }

    @Volatile private var sink: ((String) -> Boolean)? = null

    @Volatile private var done = CountDownLatch(0)

    @Volatile private var ok = false

    private val back = Messenger(
        Handler(rx.looper) { m ->
            when (m.what) {
                ChatWire.PIECE -> if (sink?.invoke(m.data.getString(ChatWire.TEXT).orEmpty()) == false) cancel()
                ChatWire.REPLY -> {
                    ok = m.data.getBoolean(ChatWire.OK) && m.data.getString(ChatWire.ERROR) == null
                    done.countDown()
                }
            }
            true
        },
    )

    fun cancel() {
        try {
            to.send(Message.obtain(null, ChatWire.CANCEL))
        } catch (_: RemoteException) {
        }
    }

    private fun call(what: Int, d: Bundle, secs: Long, on: ((String) -> Boolean)?): Boolean {
        val latch = CountDownLatch(1)
        done = latch
        sink = on
        ok = false
        try {
            to.send(Message.obtain(null, what).apply { replyTo = back; data = d })
            if (!latch.await(secs, TimeUnit.SECONDS)) {
                cancel()
                link.dead = true
                return false
            }
            return ok
        } catch (_: RemoteException) {
            link.dead = true
            return false
        } finally {
            sink = null
        }
    }

    fun run(prompt: String, pin: String, grammar: String, max: Int, threads: Int, pre: Int, temp: Float, prio: Int, on: (String) -> Boolean) = call(
        ChatWire.RUN,
        Bundle().apply {
            putString(ChatWire.PROMPT, prompt)
            putString(ChatWire.PIN, pin)
            putString(ChatWire.GRAMMAR, grammar)
            putInt(ChatWire.MAX, max)
            putInt(ChatWire.THREADS, threads)
            putInt(ChatWire.PRE, pre)
            putFloat(ChatWire.TEMP, temp)
            putInt(ChatWire.PRIO, prio)
        },
        RUN_SECS, on,
    )

    fun warm(pin: String, pre: Int, prio: Int) = call(ChatWire.WARM, Bundle().apply { putString(ChatWire.PIN, pin); putInt(ChatWire.PRE, pre); putInt(ChatWire.PRIO, prio) }, WARM_SECS, null)

    override fun close() {
        rx.quitSafely()
        c.unbindService(link)
    }

    companion object {
        private const val BIND_SECS = 5L
        private const val LOAD_SECS = 90L
        private const val WARM_SECS = 60L
        private const val RUN_SECS = 180L

        fun open(c: Context, model: File, threads: Int): ChatClient {
            val link = Link()
            check(c.bindService(Intent(c, ChatService::class.java), link, Context.BIND_AUTO_CREATE))
            try {
                check(link.ready.await(BIND_SECS, TimeUnit.SECONDS))
                val to = Messenger(link.binder)
                val r = ParcelFileDescriptor.open(model, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    ask(to, ChatWire.LOAD, Bundle().apply { putParcelable(ChatWire.MODEL, fd); putInt(ChatWire.THREADS, threads) }, LOAD_SECS)
                }
                check(r != null && r.getString(ChatWire.ERROR) == null)
                return ChatClient(c, link, to, r.getLong(ChatWire.MB))
            } catch (e: Throwable) {
                c.unbindService(link)
                throw e
            }
        }
    }
}
