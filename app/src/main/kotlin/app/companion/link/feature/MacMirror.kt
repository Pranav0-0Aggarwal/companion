package app.companion.link.feature

import app.companion.core.BridgeWire
import app.companion.core.BulkFrame
import app.companion.core.BulkPipe
import app.companion.core.Json
import app.companion.core.VideoFrame
import app.companion.link.LinkFeature
import app.companion.link.LinkFeatures
import app.companion.link.Obj
import app.companion.link.bulk.Bulk
import app.companion.link.powers.BridgeClient
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object MacMirror : LinkFeature {
    override val key = "mirror"
    override val cmds = emptySet<String>()
    private const val BEAT = 5_000L
    private const val DEAD = 15_000L
    private val BEAT_MSG = mapOf("t" to "ka")
    private val args = setOf("on", "maxH", "fps", "bps")
    private var bridge: () -> BridgeClient = { error("unset") }
    @Volatile private var pipe: BulkPipe? = null

    override fun stop() {
        pipe?.close()
    }

    fun register(bridge: () -> BridgeClient) {
        this.bridge = bridge
        LinkFeatures.all += this
        Bulk.handlers["mirror"] = ::relay
    }

    private suspend fun relay(p: BulkPipe) = coroutineScope {
        val b = bridge().takeIf { it.up.value } ?: return@coroutineScope p.sendJson(why("powers"))
        val old = pipe
        pipe = p
        old?.close()
        val video: (ByteArray) -> Unit = { body ->
            runCatching { VideoFrame.split(body).forEach { p.send(BulkFrame.VIDEO, it) } }.onFailure { p.close() }
        }
        val meta: (ByteArray) -> Unit = { body ->
            runCatching {
                val t = Json.obj(body.decodeToString())["t"]
                if (t == "size" || t == "mirror") p.send(BulkFrame.JSON, body)
                if (t == "mirror") p.close()
            }.onFailure { p.close() }
        }
        b.on[BridgeWire.VIDEO] = video
        b.on[BridgeWire.JSON] = meta
        val seen = AtomicLong(now())
        val gone = launch {
            b.up.first { !it }
            p.close()
        }
        val dog = launch {
            while (now() - seen.get() < DEAD) {
                delay(BEAT)
                runCatching { p.sendJson(BEAT_MSG) }.onFailure { p.close() }
            }
            p.close()
        }
        try {
            while (true) {
                val (t, body) = p.read()
                seen.set(now())
                when (t) {
                    BulkFrame.INPUT -> b.stream(BridgeWire.INPUT, body)
                    BulkFrame.JSON -> json(p, b, Json.obj(body.decodeToString()))
                }
            }
        } finally {
            gone.cancel()
            dog.cancel()
            b.on.remove(BridgeWire.VIDEO, video)
            b.on.remove(BridgeWire.JSON, meta)
            if (pipe === p) {
                pipe = null
                withContext(NonCancellable) { runCatching { b.cmd("mirror", mapOf("on" to false)) } }
            }
        }
    }

    private suspend fun json(p: BulkPipe, b: BridgeClient, q: Obj) {
        if (q["t"] != "mirror") return
        val r = runCatching { b.cmd("mirror", q.filterKeys { it in args }) }.getOrNull()
        if (r?.get("ok") == true) return
        p.sendJson(why(r?.get("err") as? String ?: "powers"))
        p.close()
    }

    private fun now() = System.nanoTime() / 1_000_000

    private fun why(w: String) = mapOf("t" to "mirror", "ok" to false, "why" to w)
}
