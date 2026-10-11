package app.companion.link.powers

import android.net.LocalSocket
import android.net.LocalSocketAddress
import app.companion.core.BridgeWire
import app.companion.core.Json
import app.companion.link.LinkBad
import app.companion.link.Obj
import java.io.DataInputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class BridgeClient(private val scope: CoroutineScope) {
    private val state = MutableStateFlow(false)
    private val pend = ConcurrentHashMap<Long, CompletableDeferred<Obj>>()
    private val ids = AtomicLong()
    private var sock: LocalSocket? = null
    val up: StateFlow<Boolean> = state
    @Volatile var onFrame: ((Int, ByteArray) -> Unit)? = null

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        close()
        val s = LocalSocket()
        try {
            s.connect(LocalSocketAddress(NAME, LocalSocketAddress.Namespace.ABSTRACT))
        } catch (_: IOException) {
            s.close()
            return@withContext false
        }
        synchronized(this@BridgeClient) {
            sock = s
            state.value = true
        }
        scope.launch(Dispatchers.IO) { read(s) }
        true
    }

    suspend fun cmd(t: String, p: Obj = emptyMap()): Obj {
        val id = ids.incrementAndGet()
        val d = CompletableDeferred<Obj>().also { pend[id] = it }
        try {
            withContext(Dispatchers.IO) { send(BridgeWire.JSON, Json.write(p + mapOf("t" to t, "id" to id)).encodeToByteArray()) }
            return withTimeoutOrNull(TIMEOUT) { d.await() } ?: throw LinkBad("bridge")
        } finally {
            pend -= id
        }
    }

    fun stream(type: Int, body: ByteArray) = send(type, body)

    fun close() = end(sock)

    private fun send(type: Int, body: ByteArray) {
        val s = sock ?: throw LinkBad("bridge")
        try {
            synchronized(s) { BridgeWire.write(s.outputStream, type, body) }
        } catch (e: IOException) {
            end(s)
            throw LinkBad("bridge")
        }
    }

    private fun read(s: LocalSocket) {
        try {
            val input = DataInputStream(s.inputStream.buffered())
            while (true) {
                val (t, body) = BridgeWire.read(input)
                val j = if (t == BridgeWire.JSON) runCatching { Json.obj(body.decodeToString()) }.getOrNull() else null
                val d = (j?.get("id") as? Number)?.let { pend[it.toLong()] }
                if (d != null) d.complete(j) else onFrame?.invoke(t, body)
            }
        } catch (_: IOException) {
        } finally {
            end(s)
        }
    }

    @Synchronized
    private fun end(s: LocalSocket?) {
        if (s == null) return
        runCatching(s::close)
        if (s !== sock) return
        sock = null
        state.value = false
        pend.values.forEach { it.completeExceptionally(LinkBad("bridge")) }
        pend.clear()
    }

    private companion object {
        const val NAME = "companion.bridge"
        const val TIMEOUT = 5_000L
    }
}
