package app.companion.link.feature

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.ContentObserver
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns.DATE_ADDED
import android.provider.MediaStore.MediaColumns.DISPLAY_NAME
import android.provider.MediaStore.MediaColumns.HEIGHT
import android.provider.MediaStore.MediaColumns.RELATIVE_PATH
import android.provider.MediaStore.MediaColumns.WIDTH
import android.provider.MediaStore.MediaColumns._ID
import android.provider.OpenableColumns
import android.util.Size
import android.webkit.MimeTypeMap
import app.companion.core.Expiring
import app.companion.core.FileFrames
import app.companion.core.LinkError
import app.companion.core.LinkMsg
import app.companion.core.SendGuard
import app.companion.core.ShotFilter
import app.companion.link.LinkArgs
import app.companion.link.LinkFeature
import app.companion.link.LinkFeatures
import app.companion.link.Obj
import app.companion.link.bulk.Bulk
import app.companion.ui.has
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private lateinit var ctx: Application

private const val EDGE = 320
private const val TTL = 600_000L
private val images = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

private fun FileFrames.Item.named(base: String) = FileFrames.Item("$base.${MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "img"}", size, mime, input)

private fun thumb(u: Uri) = runCatching { ctx.contentResolver.loadThumbnail(u, Size(EDGE, EDGE), null) }.getOrNull()?.let { b -> Jpeg.b64(b, EDGE, 60)?.let { b to it } }

object Shots {
    fun register(app: Application) {
        ctx = app
        LinkFeatures.all += Photos
        LinkFeatures.all += ClipImage
        Bulk.handlers["shot"] = { p ->
            val id = p.ref?.toLongOrNull()?.takeIf { it in Photos.sent } ?: throw LinkError("gone")
            FileFrames.send(p, listOf((Files.open(app, ContentUris.withAppendedId(images, id)) ?: throw LinkError("gone")).named("$id")))
        }
        Bulk.handlers["clip_in"] = { p ->
            val f = p.ref?.let(ClipImage.waits::get) ?: throw LinkError("gone")
            try {
                val m = ClipImage.Mem()
                FileFrames.receive(p, m)
                f.complete(m.bytes().takeIf { it.isNotEmpty() } ?: throw LinkError("size"))
            } catch (e: Throwable) {
                f.completeExceptionally(e)
                throw e
            }
        }
        Bulk.handlers["clip_out"] = { p ->
            val u = ClipImage.outbox.take(p.ref) ?: throw LinkError("gone")
            FileFrames.send(p, listOf((Files.open(app, u) ?: throw LinkError("gone")).named("clip")))
        }
    }
}

object Photos : LinkFeature {
    override val key = "photos"
    override val cmds = emptySet<String>()
    private const val KEEP = 32
    private const val WINDOW = 10L
    private const val SETTLE = 300L
    internal val sent: MutableMap<Long, Unit> = Collections.synchronizedMap(object : LinkedHashMap<Long, Unit>() {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<Long, Unit>) = size > KEEP
    })
    @Volatile private var obs: ContentObserver? = null
    @Volatile private var job: Job? = null

    @Synchronized
    override fun start(sc: CoroutineScope, out: (Obj) -> Unit) {
        stop()
        if (!ctx.has(Manifest.permission.READ_MEDIA_IMAGES)) return
        val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(self: Boolean) {
                job?.cancel()
                job = sc.launch(Dispatchers.IO) {
                    delay(SETTLE)
                    scan()?.let { if (isActive) out(it) }
                }
            }
        }
        ctx.contentResolver.registerContentObserver(images, true, o)
        obs = o
    }

    @Synchronized
    override fun stop() {
        job?.cancel()
        job = null
        obs?.let { runCatching { ctx.contentResolver.unregisterContentObserver(it) } }
        obs = null
        sent.clear()
    }

    private fun scan(): Obj? {
        val since = System.currentTimeMillis() / 1000 - WINDOW
        ctx.contentResolver.query(images, arrayOf(_ID, RELATIVE_PATH, DISPLAY_NAME, WIDTH, HEIGHT), "$DATE_ADDED >= ?", arrayOf("$since"), "$DATE_ADDED DESC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                if (id in sent || !ShotFilter.isShot(c.getString(1), c.getString(2))) continue
                val (_, t) = thumb(ContentUris.withAppendedId(images, id)) ?: continue
                sent[id] = Unit
                return LinkMsg.shot(id, c.getInt(3), c.getInt(4), t)
            }
        }
        return null
    }
}

object ClipImage : LinkFeature {
    override val key = "clipImage"
    override val cmds = setOf("clip_img")
    private const val WAIT = 10L
    private const val CAP = 16 shl 20
    internal val outbox = Expiring<Uri>(TTL, SystemClock::elapsedRealtime)
    internal val waits = ConcurrentHashMap<String, CompletableFuture<ByteArray>>()
    @Volatile private var offer: Pair<String, Long>? = null
    @Volatile private var push: ((Obj) -> Unit)? = null

    internal class Mem : FileFrames.Sink {
        private val buf = ByteArrayOutputStream()
        private var begun = false

        override fun begin(name: String, size: Long) {
            if (begun || size > CAP) throw LinkError("size")
            begun = true
        }

        override fun write(b: ByteArray) = buf.write(b)
        override fun finish() {}
        override fun abort() {}
        fun bytes(): ByteArray = buf.toByteArray()
    }

    override suspend fun run(t: String, q: Obj): Obj {
        val ref = LinkArgs.text(q, "ref", 64)
        offer = ref to SystemClock.elapsedRealtime() + TTL
        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newUri(ctx.contentResolver, "Mac image", ClipProvider.uri(ref)))
        return emptyMap()
    }

    override fun start(sc: CoroutineScope, out: (Obj) -> Unit) {
        push = out
    }

    override fun stop() {
        push = null
        offer = null
        outbox.clear()
        waits.values.forEach { it.cancel(true) }
    }

    @Synchronized
    fun fetch(ref: String): ByteArray {
        val o = offer
        val send = push
        if (send == null || o == null || o.first != ref || SystemClock.elapsedRealtime() > o.second) throw FileNotFoundException()
        val f = CompletableFuture<ByteArray>().also { waits[ref] = it }
        try {
            send(LinkMsg.bulkWant("clip_in", ref))
            return f.get(WAIT, TimeUnit.SECONDS)
        } catch (e: Exception) {
            throw FileNotFoundException()
        } finally {
            waits.remove(ref)
        }
    }

    fun event(u: Uri): Obj? {
        if (!SendGuard.ok(u.scheme, u.authority, ctx.packageName) { ctx.packageManager.resolveContentProvider(it, 0)?.packageName }) return null
        val (b, t) = thumb(u) ?: return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o) } }
        return LinkMsg.clipImg(outbox.put(u), o.outWidth.takeIf { it > 0 } ?: b.width, o.outHeight.takeIf { it > 0 } ?: b.height, t)
    }
}

class ClipProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        auth = "${context?.packageName}.clips"
        return true
    }

    override fun getType(uri: Uri) = "image/png"

    override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor {
        val cols = p ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(cols).apply { addRow(cols.map { if (it == OpenableColumns.DISPLAY_NAME) "Mac image.png" else null }) }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException()
        val bytes = ClipImage.fetch(uri.lastPathSegment ?: throw FileNotFoundException())
        val (r, w) = ParcelFileDescriptor.createPipe()
        Thread { ParcelFileDescriptor.AutoCloseOutputStream(w).use { runCatching { it.write(bytes) } } }.start()
        return r
    }

    override fun insert(uri: Uri, v: ContentValues?): Uri? = null
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0
    override fun delete(uri: Uri, s: String?, a: Array<String>?) = 0

    companion object {
        private var auth = ""

        fun uri(ref: String): Uri = Uri.Builder().scheme("content").authority(auth).appendPath(ref).build()
    }
}
