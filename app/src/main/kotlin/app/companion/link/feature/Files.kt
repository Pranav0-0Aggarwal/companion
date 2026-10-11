package app.companion.link.feature

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import app.companion.R
import app.companion.core.Expiring
import app.companion.core.FileFrames
import app.companion.core.LinkError
import app.companion.core.LinkMsg
import app.companion.core.SafeName
import app.companion.link.LinkFeature
import app.companion.link.LinkFeatures
import app.companion.link.Stage
import app.companion.link.bulk.Bulk
import app.companion.sl
import app.companion.ui.has
import java.io.IOException
import java.io.OutputStream

object Files : LinkFeature {
    override val key = "files"
    override val cmds = emptySet<String>()
    private const val CHANNEL = "files"
    private val pending = Expiring<List<FileFrames.Item>>(60_000, SystemClock::elapsedRealtime) { l -> l.forEach { runCatching(it::close) } }

    override fun stop() = pending.clear()

    fun register(app: Application) {
        LinkFeatures.all += this
        Bulk.handlers["files_in"] = { p -> notify(app, FileFrames.receive(p, Downloads(app))) }
        Bulk.handlers["files_out"] = { p -> FileFrames.send(p, pending.take(p.ref) ?: throw LinkError("gone")) }
    }

    fun send(c: Context, i: Intent) {
        val link = c.sl.link
        val st = link.state.value
        when {
            st.stage != Stage.Connected -> toast(c, "Mac not connected")
            key !in st.shares -> toast(c, "Turn on Files in Companion")
            else -> {
                val items = uris(i).mapNotNull { open(c, it) }
                if (items.isEmpty()) toast(c, "Nothing to send") else link.push(LinkMsg.bulkWant("files_out", pending.put(items)))
            }
        }
    }

    private fun uris(i: Intent): List<Uri> =
        if (i.action == Intent.ACTION_SEND_MULTIPLE) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else listOfNotNull(i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))

    private fun open(c: Context, u: Uri): FileFrames.Item? = runCatching {
        val r = c.contentResolver
        val pfd = r.openFileDescriptor(u, "r") ?: return null
        val (name, col) = r.query(u, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) to (if (it.isNull(1)) -1L else it.getLong(1)) else null
        } ?: (null to -1L)
        val size = pfd.statSize.takeIf { it >= 0 } ?: col
        if (size < 0) return pfd.close().let { null }
        FileFrames.Item(name ?: u.lastPathSegment ?: "file", size, r.getType(u) ?: "application/octet-stream", ParcelFileDescriptor.AutoCloseInputStream(pfd))
    }.getOrNull()

    private fun toast(c: Context, t: String) = Toast.makeText(c.applicationContext, t, Toast.LENGTH_SHORT).show()

    private fun notify(c: Context, n: Int) {
        if (n == 0 || !c.has(Manifest.permission.POST_NOTIFICATIONS)) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Files from Mac", NotificationManager.IMPORTANCE_DEFAULT))
        nm.notify(
            CHANNEL.hashCode(),
            Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentTitle(if (n == 1) "1 file from Mac" else "$n files from Mac")
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(c, 0, Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE))
                .build(),
        )
    }

    private class Downloads(private val c: Context) : FileFrames.Sink {
        private var uri: Uri? = null
        private var out: OutputStream? = null

        override fun begin(name: String, size: Long) {
            val v = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, SafeName.of(name))
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/Notchy/")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val u = c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: throw IOException("insert")
            uri = u
            out = c.contentResolver.openOutputStream(u) ?: throw IOException("open")
        }

        override fun write(b: ByteArray) = checkNotNull(out).write(b)

        override fun finish() {
            checkNotNull(out).close()
            c.contentResolver.update(checkNotNull(uri), ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            uri = null
            out = null
        }

        override fun abort() {
            runCatching { out?.close() }
            uri?.let { runCatching { c.contentResolver.delete(it, null, null) } }
            uri = null
            out = null
        }
    }
}

class SendToMacActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Files.send(this, intent)
        finish()
    }
}
