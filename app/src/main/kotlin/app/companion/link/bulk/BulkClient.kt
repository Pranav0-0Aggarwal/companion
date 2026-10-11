package app.companion.link.bulk

import app.companion.core.BulkAddr
import app.companion.core.BulkCrypto
import app.companion.core.BulkFrame
import app.companion.core.BulkPipe
import app.companion.core.LinkCrypto
import app.companion.link.LinkBad
import app.companion.link.Obj
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Bulk {
    val handlers = mutableMapOf<String, suspend (BulkPipe) -> Unit>()
    private val shares = mapOf("call" to "calls", "mirror" to "mirror", "files_in" to "files", "files_out" to "files", "shot" to "photos", "clip_in" to "clipImage", "clip_out" to "clipImage")
    private val long = setOf("call", "mirror")
    private val live = ConcurrentHashMap.newKeySet<Socket>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun share(q: Obj) = shares[purpose(q)] ?: throw LinkBad("purpose")

    suspend fun open(okm: ByteArray, q: Obj): Obj {
        val purpose = purpose(q)
        val run = handlers.getValue(purpose)
        val ip = (q["ip"] as? String)?.also { runCatching { BulkAddr.check(it) }.getOrElse { throw LinkBad("ip") } } ?: throw LinkBad("ip")
        val port = (q["port"] as? Number)?.toInt()?.takeIf { it in 1..65_535 } ?: throw LinkBad("port")
        val token = (q["token"] as? String)?.let { runCatching { LinkCrypto.unb64(it) }.getOrNull() }?.takeIf { it.size == 16 } ?: throw LinkBad("token")
        val sock = Socket().also(live::add)
        val pipe = try {
            withContext(Dispatchers.IO) {
                sock.connect(InetSocketAddress(ip, port), 3000)
                sock.soTimeout = if (purpose in long) 0 else 20_000
                sock.getOutputStream().apply { write(token); flush() }
            }
            BulkPipe(sock, BulkFrame(BulkCrypto.phone(okm, token, purpose)), purpose, q["ref"] as? String)
        } catch (e: Throwable) {
            live -= sock
            sock.close()
            throw if (e is IOException) LinkBad("nowifi") else e
        }
        start(sock, pipe, run)
        return emptyMap()
    }

    internal fun start(sock: Socket, pipe: BulkPipe, run: suspend (BulkPipe) -> Unit) {
        live += sock
        scope.launch {
            try {
                pipe.use { runCatching { run(it) } }
            } finally {
                live -= sock
            }
        }
    }

    fun closeAll() = live.forEach { runCatching(it::close) }

    private fun purpose(q: Obj) = (q["purpose"] as? String)?.takeIf { it in handlers } ?: throw LinkBad("purpose")
}
