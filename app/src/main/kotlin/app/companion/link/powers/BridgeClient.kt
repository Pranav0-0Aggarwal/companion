package app.companion.link.powers

import app.companion.core.BridgeAuth
import app.companion.core.BridgeWire
import app.companion.core.Json
import app.companion.link.Diag
import app.companion.link.LinkBad
import app.companion.link.Obj
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
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

open class BridgeClient(private val scope: CoroutineScope) {
    private val state = MutableStateFlow(false)
    private val pend = ConcurrentHashMap<Long, CompletableDeferred<Obj>>()
    private val ids = AtomicLong()
    private val rnd = SecureRandom()
    private var sock: Socket? = null
    @Volatile var key: ByteArray? = null
    open val up: StateFlow<Boolean> = state
    val on = ConcurrentHashMap<Int, (ByteArray) -> Unit>()

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        close()
        val k = key ?: return@withContext false
        val s = Socket()
        try {
            s.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), BridgeAuth.PORT), 1000)
            s.soTimeout = 3000
            s.tcpNoDelay = true
            if (!BridgeAuth.client(DataInputStream(s.inputStream), DataOutputStream(s.outputStream), k) { n -> ByteArray(n).also(rnd::nextBytes) }) {
                s.close()
                Diag.log("bridge connect: auth failed")
                return@withContext false
            }
            s.soTimeout = 0
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

    open suspend fun cmd(t: String, p: Obj = emptyMap()): Obj {
        val id = ids.incrementAndGet()
        val d = CompletableDeferred<Obj>().also { pend[id] = it }
        try {
            withContext(Dispatchers.IO) { send(BridgeWire.JSON, Json.write(p + mapOf("t" to t, "id" to id)).encodeToByteArray()) }
            return withTimeoutOrNull(TIMEOUT) { d.await() } ?: throw LinkBad("bridge")
        } finally {
            pend -= id
        }
    }

    open fun stream(type: Int, body: ByteArray) = send(type, body)

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

    private fun read(s: Socket) {
        try {
            val input = DataInputStream(s.inputStream.buffered())
            while (true) {
                val (t, body) = BridgeWire.read(input)
                val j = if (t == BridgeWire.JSON) runCatching { Json.obj(body.decodeToString()) }.getOrNull() else null
                val d = (j?.get("id") as? Number)?.let { pend[it.toLong()] }
                if (d != null) d.complete(j) else on[t]?.let { runCatching { it(body) } }
            }
        } catch (_: IOException) {
        } finally {
            end(s)
        }
    }

    @Synchronized
    private fun end(s: Socket?) {
        if (s == null) return
        runCatching(s::close)
        if (s !== sock) return
        sock = null
        state.value = false
        pend.values.forEach { it.completeExceptionally(LinkBad("bridge")) }
        pend.clear()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
