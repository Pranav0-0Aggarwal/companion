package app.companion.link.feature

import android.app.Application
import android.app.Notification
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import app.companion.core.LinkCrypto
import app.companion.core.LinkMsg
import app.companion.core.LiveRule
import app.companion.core.LiveThrottle
import app.companion.ingest.NotifService
import app.companion.link.LinkBad
import app.companion.link.LinkFeature
import app.companion.link.Obj
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope

class LiveFeed(private val app: Application) : LinkFeature {
    override val key = "live"
    override val cmds = setOf("media_cmd")

    private data class Mark(val pkg: String, val title: String, val artist: String, val playing: Boolean, val dur: Long, val actions: Long)

    private val throttle = LiveThrottle()
    private val sent = ConcurrentHashMap.newKeySet<String>()
    private val main = Handler(Looper.getMainLooper())
    private val mgr = app.getSystemService(MediaSessionManager::class.java)
    private val who = ComponentName(app, NotifService::class.java)
    @Volatile private var out: ((Obj) -> Unit)? = null
    private var ctls = emptyList<MediaController>()
    private var cur: MediaController? = null
    private var mark: Mark? = null
    private var pos = 0L
    private var at = 0L
    private var artKey = ""
    private var art: String? = null
    private var fresh = false
    private val pending = ConcurrentHashMap<String, StatusBarNotification>()

    private fun PlaybackState?.live() = this?.state.let { it == PlaybackState.STATE_PLAYING || it == PlaybackState.STATE_BUFFERING }

    private val cb = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = sync()
        override fun onPlaybackStateChanged(state: PlaybackState?) = sync()
    }
    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { watch(it.orEmpty()) }

    override suspend fun run(t: String, q: Obj): Obj {
        val tc = synchronized(this) { cur }?.transportControls ?: throw LinkBad("gone")
        when (q["cmd"]) {
            "play" -> tc.play()
            "pause" -> tc.pause()
            "next" -> tc.skipToNext()
            "prev" -> tc.skipToPrevious()
            "seek" -> tc.seekTo((q["pos"] as? Number)?.toLong()?.coerceAtLeast(0) ?: throw LinkBad("pos"))
            else -> throw LinkBad("cmd")
        }
        return emptyMap()
    }

    override fun start(sc: CoroutineScope, out: (Obj) -> Unit) {
        this.out = out
        on = this
        runCatching {
            mgr.addOnActiveSessionsChangedListener(sessions, who, main)
            watch(mgr.getActiveSessions(who))
        }
        NotifService.active().forEach { runCatching { post(it) } }
    }

    @Synchronized
    override fun stop() {
        if (on === this) on = null
        val send = out
        out = null
        runCatching { mgr.removeOnActiveSessionsChangedListener(sessions) }
        ctls.forEach { runCatching { it.unregisterCallback(cb) } }
        ctls = emptyList()
        cur = null
        pending.keys.forEach(main::removeCallbacksAndMessages)
        pending.clear()
        send?.let { s ->
            val now = System.currentTimeMillis()
            sent.forEach { s(LinkMsg.liveGone(it)) }
            mark?.takeIf { it.playing }?.let { s(frame(it.copy(playing = false), pos + now - at, now)) }
        }
        mark = null
        artKey = ""
        sent.forEach(throttle::forget)
        sent.clear()
    }

    private fun post(sbn: StatusBarNotification) {
        val send = out ?: return
        val n = sbn.notification
        val x = n.extras
        if (sbn.packageName == app.packageName || x.containsKey(Notification.EXTRA_MEDIA_SESSION)) return
        val max = x.getInt(Notification.EXTRA_PROGRESS_MAX)
        val chrono = x.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
        if (!LiveRule.ok(n.flags and Notification.FLAG_ONGOING_EVENT != 0, max, chrono, n.category)) return gone(sbn.key)
        val title = x.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = x.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val prog = if (max > 0 && !x.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)) x.getInt(Notification.EXTRA_PROGRESS) else null
        val now = System.currentTimeMillis()
        if (!throttle.pass(sbn.key, "${n.category}|${prog != null}|$chrono", now)) return hold(sbn, now)
        pending.remove(sbn.key)
        main.removeCallbacksAndMessages(sbn.key)
        val icon = if (sent.add(sbn.key)) n.getLargeIcon()?.loadDrawable(app)?.let { jpeg(bitmap(it)) } else null
        val down = x.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)
        val name = runCatching { app.packageManager.getApplicationInfo(sbn.packageName, 0).loadLabel(app.packageManager).toString() }.getOrDefault(sbn.packageName)
        send(LinkMsg.live(sbn.key, sbn.packageName, name, title, text, prog, prog?.let { max }, n.`when`.takeIf { chrono && down }, n.`when`.takeIf { chrono && !down }, icon))
    }

    private fun hold(sbn: StatusBarNotification, now: Long) {
        if (pending.put(sbn.key, sbn) == null) main.postDelayed({ pending.remove(sbn.key)?.let { runCatching { post(it) } } }, sbn.key, throttle.delay(sbn.key, now).coerceAtLeast(1))
    }

    private fun gone(k: String) {
        pending.remove(k)
        main.removeCallbacksAndMessages(k)
        if (!sent.remove(k)) return
        throttle.forget(k)
        out?.invoke(LinkMsg.liveGone(k))
    }

    @Synchronized
    private fun watch(l: List<MediaController>) {
        ctls.forEach { runCatching { it.unregisterCallback(cb) } }
        ctls = l
        l.forEach { it.registerCallback(cb, main) }
        sync()
    }

    @Synchronized
    private fun sync() {
        val send = out ?: return
        val c = ctls.firstOrNull { it.playbackState.live() } ?: ctls.firstOrNull { it.sessionToken == cur?.sessionToken }
        cur = c
        val prev = mark
        val now = System.currentTimeMillis()
        if (c == null) {
            mark = null
            if (prev?.playing == true) send(frame(prev.copy(playing = false), pos + now - at, now))
            return
        }
        val s = c.playbackState
        val md = c.metadata
        val playing = s.live()
        if (!playing && prev?.pkg != c.packageName) return
        val title = (md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)).orEmpty()
        val artist = (md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)).orEmpty()
        val m = Mark(c.packageName, title, artist, playing, md?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0, s?.actions ?: 0)
        val p = s?.let { if (playing) it.position + ((SystemClock.elapsedRealtime() - it.lastPositionUpdateTime) * it.playbackSpeed).toLong() else it.position } ?: 0
        val seeked = m == prev && abs(p - (pos + if (m.playing) now - at else 0)) > 2_000
        if (m == prev && !seeked) return
        val k = "${m.pkg}|$title|$artist"
        if (k != artKey) {
            artKey = k
            fresh = true
            art = (md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON))?.let(::jpeg)
        }
        mark = m
        pos = p
        at = now
        send(frame(m, p, now))
    }

    private fun frame(m: Mark, p: Long, now: Long) = LinkMsg.media(m.pkg, m.title, m.artist, art.takeIf { fresh }, m.playing, p, m.dur, now).also { fresh = false }

    private fun bitmap(d: Drawable) = (d as? BitmapDrawable)?.bitmap ?: Bitmap.createBitmap(EDGE, EDGE, Bitmap.Config.ARGB_8888).also {
        d.setBounds(0, 0, EDGE, EDGE)
        d.draw(Canvas(it))
    }

    private fun jpeg(src: Bitmap): String? {
        var b = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) ?: return null else src
        val k = EDGE.toFloat() / maxOf(b.width, b.height)
        if (k < 1) b = Bitmap.createScaledBitmap(b, (b.width * k).roundToInt().coerceAtLeast(1), (b.height * k).roundToInt().coerceAtLeast(1), true)
        var q = 80
        while (true) {
            val o = ByteArrayOutputStream()
            b.compress(Bitmap.CompressFormat.JPEG, q, o)
            if (o.size() <= MAX) return LinkCrypto.b64(o.toByteArray())
            if (q > 30) q -= 20 else if (b.width > 8) b = Bitmap.createScaledBitmap(b, b.width * 3 / 4, b.height * 3 / 4, true) else return null
        }
    }

    companion object {
        private const val EDGE = 96
        private const val MAX = 16 * 1024
        @Volatile private var on: LiveFeed? = null

        fun posted(sbn: StatusBarNotification) {
            runCatching { on?.post(sbn) }
        }

        fun removed(sbn: StatusBarNotification) {
            on?.gone(sbn.key)
        }
    }
}
