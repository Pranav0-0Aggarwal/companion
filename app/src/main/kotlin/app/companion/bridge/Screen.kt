package app.companion.bridge

import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.view.Surface
import app.companion.core.BridgeWire
import app.companion.core.Json
import app.companion.core.VideoFrame

object Screen {
    @Volatile private var cur: Session? = null
    @Volatile var dim = 1 to 1
    val live get() = cur != null

    @Synchronized
    fun run(ctx: Context, q: Map<String, Any?>, tx: (Int, ByteArray) -> Unit): String? {
        stop()
        if (q["on"] != true) return null
        val s = Session(ctx, tx, arg(q, "maxH", 1280, 64..4096), arg(q, "fps", 60, 1..120), arg(q, "bps", 6_000_000, 100_000..40_000_000))
        val why = s.open()
        if (why != null) s.close() else cur = s
        return why
    }

    @Synchronized
    fun stop() {
        cur?.close()
        cur = null
        Input.release()
    }

    fun available() = Hw.info() != null

    @Synchronized
    fun release(tx: (Int, ByteArray) -> Unit) {
        if (cur?.tx === tx) stop()
    }

    @Synchronized
    internal fun redo(s: Session, force: Boolean = false) {
        if (cur === s) s.rebuild(force)?.let { fail(s, it) }
    }

    @Synchronized
    internal fun fail(s: Session, why: String) {
        if (cur !== s) return
        stop()
        runCatching { s.tx(BridgeWire.JSON, Json.write(mapOf("t" to "mirror", "ok" to false, "why" to why)).encodeToByteArray()) }
    }

    @Synchronized
    fun idr() {
        cur?.let { redo(it, true) }
    }

    private fun arg(q: Map<String, Any?>, k: String, d: Int, r: IntRange) = (q[k] as? Number)?.toInt()?.coerceIn(r) ?: d

    internal class Session(ctx: Context, val tx: (Int, ByteArray) -> Unit, private val maxH: Int, private val fps: Int, private val bps: Int) {
        private val ht = HandlerThread("mirror").apply { start() }
        private val h = Handler(ht.looper)
        private val dm = runCatching { ctx.getSystemService(DisplayManager::class.java) }.getOrNull()
        private var codec: MediaCodec? = null
        private var surface: Surface? = null
        private var out: AutoCloseable? = null
        private var info = Hw.Info(0, 0, 0, 0)
        private val watch = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) = Unit
            override fun onDisplayRemoved(id: Int) = Unit
            override fun onDisplayChanged(id: Int) {
                if (id == 0) redo(this@Session)
            }
        }
        private val cb = object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(c: MediaCodec, i: Int) = Unit
            override fun onOutputFormatChanged(c: MediaCodec, f: MediaFormat) = Unit
            override fun onError(c: MediaCodec, e: MediaCodec.CodecException) {
                h.post { Screen.fail(this@Session, "encoder") }
            }

            override fun onOutputBufferAvailable(c: MediaCodec, i: Int, b: MediaCodec.BufferInfo) {
                try {
                    if (b.size > 0) {
                        val d = c.getOutputBuffer(i)!!.apply { position(b.offset); limit(b.offset + b.size) }
                        val f = (if (b.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) VideoFrame.CONFIG else 0) or
                            (if (b.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) VideoFrame.KEY else 0)
                        tx(BridgeWire.VIDEO, VideoFrame.pack(f, b.presentationTimeUs, d))
                    }
                    c.releaseOutputBuffer(i, false)
                } catch (_: Exception) {
                    h.post { Screen.fail(this@Session, "stream") }
                }
            }
        }

        fun open(): String? {
            runCatching { dm?.registerDisplayListener(watch, h) }
            return build()
        }

        fun rebuild(force: Boolean): String? {
            val i = Hw.info() ?: return "display"
            if (!force && i.w == info.w && i.h == info.h && i.rot == info.rot) return null
            teardown()
            return build()
        }

        fun close() {
            runCatching { dm?.unregisterDisplayListener(watch) }
            teardown()
            ht.quitSafely()
        }

        private fun build(): String? {
            val i = Hw.info() ?: return "display"
            val (w, hh) = VideoFrame.fit(i.w, i.h, maxH)
            val c = runCatching { MediaCodec.createEncoderByType(MIME) }.getOrNull() ?: return "encoder"
            val f = MediaFormat.createVideoFormat(MIME, w, hh).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bps)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_MAX_FPS_TO_ENCODER, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10)
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
            }
            val s = try {
                c.setCallback(cb, h)
                c.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                c.createInputSurface().also { c.start() }
            } catch (_: Exception) {
                runCatching { c.release() }
                return "encoder"
            }
            codec = c
            surface = s
            info = i
            dim = i.w to i.h
            try {
                tx(BridgeWire.JSON, Json.write(mapOf("t" to "size", "w" to i.w, "h" to i.h)).encodeToByteArray())
            } catch (_: Exception) {
                teardown()
                return "bridge"
            }
            out = Hw.mirror(s, w, hh, i)
            if (out == null) {
                teardown()
                return "display"
            }
            return null
        }

        private fun teardown() {
            runCatching { out?.close() }
            out = null
            codec?.let { c ->
                runCatching { c.stop() }
                runCatching { c.release() }
            }
            codec = null
            runCatching { surface?.release() }
            surface = null
        }
    }

    private object Hw {
        class Info(val w: Int, val h: Int, val rot: Int, val stack: Int)

        fun info(): Info? = runCatching {
            val g = Class.forName("android.hardware.display.DisplayManagerGlobal")
            val d = g.getMethod("getDisplayInfo", Int::class.javaPrimitiveType).invoke(g.getMethod("getInstance").invoke(null), 0)!!
            fun f(n: String) = d.javaClass.getField(n).getInt(d)
            Info(f("logicalWidth"), f("logicalHeight"), f("rotation"), f("layerStack"))
        }.getOrNull()

        fun mirror(sf: Surface, w: Int, h: Int, i: Info): AutoCloseable? = virtual(sf, w, h) ?: legacy(sf, w, h, i)

        private fun virtual(sf: Surface, w: Int, h: Int): AutoCloseable? = runCatching {
            val int = Int::class.javaPrimitiveType
            val m = DisplayManager::class.java.getDeclaredMethod("createVirtualDisplay", String::class.java, int, int, int, Surface::class.java)
            val v = m.invoke(null, "companion", w, h, 0, sf) as VirtualDisplay
            AutoCloseable { v.release() }
        }.getOrNull()

        private fun legacy(sf: Surface, w: Int, h: Int, i: Info): AutoCloseable? = runCatching {
            val int = Int::class.javaPrimitiveType
            val c = Class.forName("android.view.SurfaceControl")
            val t = c.getMethod("createDisplay", String::class.java, Boolean::class.javaPrimitiveType).invoke(null, "companion", false) as IBinder
            c.getMethod("openTransaction").invoke(null)
            try {
                c.getMethod("setDisplaySurface", IBinder::class.java, Surface::class.java).invoke(null, t, sf)
                c.getMethod("setDisplayProjection", IBinder::class.java, int, Rect::class.java, Rect::class.java).invoke(null, t, 0, Rect(0, 0, i.w, i.h), Rect(0, 0, w, h))
                c.getMethod("setDisplayLayerStack", IBinder::class.java, int).invoke(null, t, i.stack)
            } finally {
                c.getMethod("closeTransaction").invoke(null)
            }
            AutoCloseable { c.getMethod("destroyDisplay", IBinder::class.java).invoke(null, t) }
        }.getOrNull()
    }

    private const val MIME = "video/avc"
}
