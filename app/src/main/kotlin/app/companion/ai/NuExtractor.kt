package app.companion.ai

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.RemoteException
import app.companion.core.Extractor
import app.companion.core.Field
import app.companion.core.Nux
import app.companion.core.Raw
import app.companion.core.text
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

object NuxWire {
    const val LOAD = 1
    const val RUN = 2
    const val REPLY = 3
    const val MODEL = "model"
    const val THREADS = "threads"
    const val PROMPT = "prompt"
    const val JSON = "json"
    const val MB = "mb"
    const val ERROR = "error"
}

class NuxService : Service() {
    private val thread = HandlerThread("nux", Process.THREAD_PRIORITY_BACKGROUND).also { it.start() }
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
                NuxWire.LOAD -> {
                    if (handle != 0L) NuxJni.free(handle)
                    handle = 0
                    handle = d.getParcelable(NuxWire.MODEL, ParcelFileDescriptor::class.java)?.use { NuxJni.load(it.fd, d.getInt(NuxWire.THREADS, THREADS)) } ?: 0
                    check(handle != 0L)
                    out.putLong(NuxWire.MB, Debug.getPss() / 1024)
                }
                NuxWire.RUN -> out.putString(NuxWire.JSON, NuxJni.run(handle, d.getString(NuxWire.PROMPT).orEmpty(), Nux.GRAMMAR, Nux.MAX_TOKENS))
            }
        } catch (_: Throwable) {
            out.putString(NuxWire.ERROR, "failed")
        }
        try {
            to.send(Message.obtain(null, NuxWire.REPLY).apply { data = out })
        } catch (_: RemoteException) {
        }
    }

    override fun onBind(intent: Intent): IBinder = messenger.binder

    override fun onDestroy() {
        if (handle != 0L) NuxJni.free(handle)
        Process.killProcess(Process.myPid())
    }

    private companion object {
        const val THREADS = 2
    }
}

class NuxClient private constructor(private val c: Context, private val link: Link, private val to: Messenger, override val mb: Long) : Runner {
    override val accel = "CPU"
    override val alive get() = !link.dead

    fun run(prompt: String): String? {
        val r = try {
            ask(to, NuxWire.RUN, Bundle().apply { putString(NuxWire.PROMPT, prompt) }, RUN_SECS)
        } catch (_: RemoteException) {
            link.dead = true
            null
        }
        return r?.getString(NuxWire.JSON)
    }

    override fun close() = c.unbindService(link)

    companion object {
        private const val BIND_SECS = 5L
        private const val LOAD_SECS = 60L
        private const val RUN_SECS = 60L

        fun open(c: Context, model: File): NuxClient {
            val link = Link()
            check(c.bindService(Intent(c, NuxService::class.java), link, Context.BIND_AUTO_CREATE))
            try {
                check(link.ready.await(BIND_SECS, TimeUnit.SECONDS))
                val to = Messenger(link.binder)
                val r = ParcelFileDescriptor.open(model, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    ask(to, NuxWire.LOAD, Bundle().apply { putParcelable(NuxWire.MODEL, fd) }, LOAD_SECS)
                }
                check(r != null && r.getString(NuxWire.ERROR) == null)
                return NuxClient(c, link, to, r.getLong(NuxWire.MB))
            } catch (e: Throwable) {
                c.unbindService(link)
                throw e
            }
        }
    }
}

class NuExtractor(private val c: Context, private val gov: Governor) : Extractor {
    override fun extract(raw: Raw, want: Set<Field>): Map<Field, String> {
        val f = Nux.keep(want)
        if (f.isEmpty()) return emptyMap()
        val model = Models.file(c, Manifest.nux) ?: return emptyMap()
        val json = try {
            runBlocking { gov.run(Manifest.nux, { NuxClient.open(c, model) }) { it.run(Nux.prompt(raw.text())) } }
        } catch (_: Exception) {
            null
        }
        return json?.let(Nux::parse).orEmpty().filterKeys { it in f }
    }
}
