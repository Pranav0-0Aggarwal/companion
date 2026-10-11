package app.companion.bridge

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import app.companion.core.Json

object Input {
    private const val PASTE = 279
    private const val RESTORE = 300L
    const val RESTORED = "app.companion.clip.restored"
    private var down = 0L
    private var touch: Pair<Int, Int>? = null
    private val inject by lazy { runCatching { bind("InputManagerGlobal") }.getOrElse { bind("InputManager") } }

    fun available() = runCatching { inject }.isSuccess

    private fun bind(name: String): (InputEvent) -> Unit {
        val c = Class.forName("android.hardware.input.$name")
        val im = c.getMethod("getInstance").invoke(null)
        val m = c.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
        return { e -> m.invoke(im, e, 0) }
    }

    fun handle(ctx: Context, body: ByteArray) {
        if (!Screen.live) return
        runCatching {
            val q = Json.obj(body.decodeToString())
            when (q["k"]) {
                "touch" -> touch(num(q, "a"), num(q, "x"), num(q, "y"))
                "scroll" -> scroll(num(q, "x"), num(q, "y"), (q["dy"] as? Number)?.toFloat() ?: 0f)
                "key" -> key(num(q, "code"))
                "text" -> (q["s"] as? String)?.let { text(ctx, it.take(256)) }
                "idr" -> Screen.idr()
            }
        }
    }

    @Synchronized
    fun release() {
        touch?.let { (x, y) -> runCatching { touch(2, x, y) } }
    }

    private fun num(q: Map<String, Any?>, k: String) = (q[k] as? Number)?.toInt() ?: -1

    @Synchronized
    private fun touch(a: Int, x: Int, y: Int) {
        val act = when (a) {
            0 -> MotionEvent.ACTION_DOWN
            1 -> MotionEvent.ACTION_MOVE
            2 -> MotionEvent.ACTION_UP
            else -> return
        }
        if (act != MotionEvent.ACTION_DOWN && touch == null) return
        val now = SystemClock.uptimeMillis()
        val (w, h) = Screen.dim
        val p = x.coerceIn(0, w - 1) to y.coerceIn(0, h - 1)
        if (act == MotionEvent.ACTION_DOWN) down = now
        touch = if (act == MotionEvent.ACTION_UP) null else p
        put(motion(down, now, act, p.first, p.second, if (act == MotionEvent.ACTION_UP) 0f else 1f, InputDevice.SOURCE_TOUCHSCREEN, MotionEvent.TOOL_TYPE_FINGER, 0f))
    }

    private fun scroll(x: Int, y: Int, dy: Float) {
        val now = SystemClock.uptimeMillis()
        val (w, h) = Screen.dim
        put(motion(now, now, MotionEvent.ACTION_SCROLL, x.coerceIn(0, w - 1), y.coerceIn(0, h - 1), 0f, InputDevice.SOURCE_MOUSE, MotionEvent.TOOL_TYPE_MOUSE, dy.coerceIn(-10f, 10f)))
    }

    private fun motion(t0: Long, t: Long, act: Int, x: Int, y: Int, pressure: Float, src: Int, tool: Int, v: Float): MotionEvent {
        val p = MotionEvent.PointerProperties().apply { id = 0; toolType = tool }
        val c = MotionEvent.PointerCoords().apply {
            this.x = x.toFloat()
            this.y = y.toFloat()
            this.pressure = pressure
            size = 1f
            setAxisValue(MotionEvent.AXIS_VSCROLL, v)
        }
        return MotionEvent.obtain(t0, t, act, 1, arrayOf(p), arrayOf(c), 0, 0, 1f, 1f, 0, 0, src, 0)
    }

    private fun key(code: Int) {
        if (code !in 1..400) return
        val now = SystemClock.uptimeMillis()
        intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP).forEach {
            put(KeyEvent(now, now, it, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD))
        }
    }

    private fun text(ctx: Context, s: String) {
        val e = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(s.toCharArray())
        if (e != null) return e.forEach(::put)
        paste(ctx, s)
    }

    private fun paste(ctx: Context, s: String) {
        val cm = ClipboardManager::class.java.getDeclaredConstructor(Context::class.java, Handler::class.java).apply { isAccessible = true }
            .newInstance(ctx, Handler(Looper.getMainLooper()))
        val saved = cm.primaryClip?.also { c ->
            c.description.extras = PersistableBundle(c.description.extras ?: PersistableBundle()).apply { putBoolean(RESTORED, true) }
        }
        cm.setPrimaryClip(ClipData.newPlainText("", s).apply { description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) } })
        key(PASTE)
        Handler(Looper.getMainLooper()).postDelayed({ runCatching { if (saved != null) cm.setPrimaryClip(saved) else cm.clearPrimaryClip() } }, RESTORE)
    }

    private fun put(e: InputEvent) {
        inject(e)
        (e as? MotionEvent)?.recycle()
    }
}
