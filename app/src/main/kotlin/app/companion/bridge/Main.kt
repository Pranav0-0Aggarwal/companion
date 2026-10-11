package app.companion.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.Build
import android.os.Looper
import android.os.PatternMatcher
import app.companion.core.BridgeWire
import app.companion.core.Json
import java.io.DataInputStream
import java.io.IOException
import kotlin.concurrent.thread
import kotlin.system.exitProcess

object Main {
    private const val NAME = "companion.bridge"
    private const val PKG = "app.companion"
    private const val PERM = "android.permission.WRITE_SECURE_SETTINGS"
    private lateinit var ctx: Context
    private var cur: LocalSocket? = null

    @JvmStatic
    fun main(args: Array<String>) {
        Looper.prepareMainLooper()
        ctx = Shell.context()
        val uid = ctx.packageManager.getPackageUid(PKG, 0)
        val srv = try {
            LocalServerSocket(NAME)
        } catch (_: IOException) {
            exitProcess(0)
        }
        val gone = IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply {
            addDataScheme("package")
            addDataSchemeSpecificPart(PKG, PatternMatcher.PATTERN_LITERAL)
        }
        ctx.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = exitProcess(0)
        }, gone, Context.RECEIVER_NOT_EXPORTED)
        thread {
            while (true) {
                val c = try {
                    srv.accept()
                } catch (_: IOException) {
                    exitProcess(0)
                }
                try {
                    if (c.peerCredentials.uid == uid) adopt(c) else c.close()
                } catch (_: Exception) {
                    runCatching(c::close)
                }
            }
        }
        Looper.loop()
    }

    @Synchronized
    private fun adopt(c: LocalSocket) {
        cur?.close()
        cur = c
        thread { serve(c) }
    }

    @Synchronized
    private fun drop(c: LocalSocket) {
        if (cur === c) cur = null
        c.close()
    }

    private fun serve(c: LocalSocket) {
        val tx = { t: Int, b: ByteArray -> synchronized(c) { BridgeWire.write(c.outputStream, t, b) } }
        try {
            val input = DataInputStream(c.inputStream.buffered())
            while (true) {
                val (t, body) = BridgeWire.read(input)
                when (t) {
                    BridgeWire.JSON -> tx(BridgeWire.JSON, Json.write(run(Json.obj(body.decodeToString()), tx)).encodeToByteArray())
                    BridgeWire.INPUT -> Input.handle(ctx, body)
                    else -> Unit
                }
            }
        } catch (_: Exception) {
        } finally {
            Screen.release(tx)
            drop(c)
        }
    }

    private fun run(q: Map<String, Any?>, tx: (Int, ByteArray) -> Unit): Map<String, Any?> {
        val id = q["id"]
        return when (q["t"]) {
            "caps" -> res(id, true, "data" to caps())
            "grant" -> res(id, grant())
            "mirror" -> Screen.run(ctx, q, tx).let { if (it == null) res(id, true) else res(id, false, "err" to it) }
            else -> res(id, false, "err" to "unknown")
        }
    }

    private fun res(id: Any?, ok: Boolean, vararg more: Pair<String, Any?>) = mapOf("t" to "res", "id" to id, "ok" to ok) + more

    private fun caps() = mapOf("audio" to audio(), "mirror" to true, "input" to true, "sdk" to Build.VERSION.SDK_INT)

    private fun audio() = runCatching { Shell.audio(ctx).let { it.javaClass.getMethod("isPstnCallAudioInterceptable").invoke(it) as Boolean } }.getOrDefault(false)

    private fun grant() = runCatching { Runtime.getRuntime().exec(arrayOf("/system/bin/pm", "grant", PKG, PERM)).waitFor() == 0 }.getOrDefault(false)
}
