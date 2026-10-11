package app.companion.link.feature

import android.os.SystemClock
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
        val prev = b.onFrame
        val mine: (Int, ByteArray) -> Unit = { t, body ->
            runCatching {
                when {
                    t == BridgeWire.VIDEO -> VideoFrame.split(body).forEach { p.send(BulkFrame.VIDEO, it) }
                    t == BridgeWire.JSON && Json.obj(body.decodeToString())["t"] == "size" -> p.send(BulkFrame.JSON, body)
                    else -> prev?.invoke(t, body)
                }
            }.onFailure { p.close() }
        }
        b.onFrame = mine
        val seen = AtomicLong(SystemClock.elapsedRealtime())
        val gone = launch {
            b.up.first { !it }
            p.close()
        }
        val dog = launch {
            while (SystemClock.elapsedRealtime() - seen.get() < DEAD) delay(BEAT)
            p.close()
        }
        try {
            while (true) {
                val (t, body) = p.read()
                seen.set(SystemClock.elapsedRealtime())
                when (t) {
                    BulkFrame.INPUT -> b.stream(BridgeWire.INPUT, body)
                    BulkFrame.JSON -> json(p, b, Json.obj(body.decodeToString()))
                }
            }
        } finally {
            gone.cancel()
            dog.cancel()
            if (b.onFrame === mine) b.onFrame = prev
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

    private fun why(w: String) = mapOf("t" to "mirror", "ok" to false, "why" to w)
}
