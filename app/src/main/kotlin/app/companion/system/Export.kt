package app.companion.system

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.companion.core.Corrections
import app.companion.data.Repo
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object Export {
    private const val DIR = "exports"
    private const val FILE = "companion-corrections.jsonl"
    private const val PREFS = "corrections"
    private const val KEY = "exported"
    private const val GRACE = 60_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun dir(c: Context) = File(c.cacheDir, DIR)

    fun sweep(c: Context) {
        dir(c).deleteRecursively()
    }

    suspend fun write(c: Context, repo: Repo): File? {
        val rows = repo.exportRows()
        if (rows.isEmpty()) return null
        sweep(c)
        val f = File(dir(c).also { it.mkdirs() }, FILE)
        f.writeText(rows.joinToString("\n", postfix = "\n") { Corrections.line(it.sender, it.title, it.note, it.body, it.task, it.model, it.prob, it.chosen) })
        return f
    }

    fun chooser(c: Context, f: File): Intent {
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/x-ndjson")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri("", uri)
        return Intent.createChooser(send, "Export corrections")
    }

    fun discard(c: Context) {
        scope.launch {
            delay(GRACE)
            sweep(c)
        }
    }

    fun last(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY, 0L)

    fun mark(c: Context, at: Long) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY, at).apply()
    }
}
