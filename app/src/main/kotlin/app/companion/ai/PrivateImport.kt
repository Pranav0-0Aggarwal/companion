package app.companion.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.companion.core.Line
import app.companion.core.Show
import app.companion.core.PrivateFiles as Pf
import app.companion.sl
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Imp(val busy: Boolean = false, val note: String = "", val done: Long = 0, val total: Long = 0, val lines: List<Line> = emptyList(), val rev: Int = 0)

object PrivateImport {
    private class Pick(val uri: Uri, val name: String?, val size: Long)

    private class Bad(val name: String, val why: String) : Exception()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val st = MutableStateFlow(Imp())
    val state: StateFlow<Imp> = st.asStateFlow()

    private fun busy(note: String) = st.getAndUpdate { if (it.busy) it else Imp(true, note, rev = it.rev) }.busy

    fun start(c: Context, uris: List<Uri>) {
        if (uris.isEmpty() || busy("Reading names")) return
        val app = c.applicationContext
        scope.launch { run(app, uris) }
    }

    fun remove(c: Context) {
        if (busy("Removing")) return
        val app = c.applicationContext
        scope.launch {
            Models.custom(app).deleteRecursively()
            done(app, listOf(Line("Private models", true, "removed, base models in use")))
        }
    }

    private fun stage(c: Context) = File(c.noBackupFilesDir, "models/import")

    private suspend fun done(c: Context, lines: List<Line>) {
        c.sl.gov.release()
        Xnn.prune(c)
        Processing.verify(c)
        st.update { Imp(lines = lines, rev = it.rev + 1) }
    }

    private suspend fun run(c: Context, uris: List<Uri>) {
        val stage = stage(c)
        var ok = false
        val lines = try {
            work(c, uris, stage).also { ok = Pf.commit(it) }
        } catch (e: Bad) {
            listOf(Line(e.name, false, e.why))
        } catch (e: Exception) {
            listOf(Line("Import", false, "failed: ${e.javaClass.simpleName}"))
        } finally {
            stage.deleteRecursively()
            File(stage.parentFile, "previous").deleteRecursively()
        }
        if (ok) done(c, lines) else st.update { Imp(lines = lines, rev = it.rev) }
    }

    private fun work(c: Context, uris: List<Uri>, stage: File): List<Line> {
        stage.deleteRecursively()
        stage.mkdirs()
        val all = uris.map { meta(c, it) }
        val names = all.map { it.name }
        val picks = all.filterNot { Pf.skip(it.name) }
        val room = Pf.room(picks.map { it.name }, picks.map { it.size }, stage.usableSpace)
        if (room.isNotEmpty()) return room
        val total = picks.sumOf { maxOf(it.size, 0) }
        var read = 0L
        val sums = HashMap<String, String>()
        fun take(p: Pick, n: String, cap: Long) {
            sums[n] = copy(c, p.uri, File(stage, n), n, cap) { b ->
                read += b
                st.update { it.copy(note = "Copying $n", done = read, total = total) }
            }
        }
        picks.filter { Pf.plain(it.name).let { n -> n == Pf.SPEC || n == Pf.MANIFEST } }.forEach { take(it, it.name!!, Pf.SMALL) }
        val spec = File(stage, Pf.SPEC).takeIf { it.isFile } ?: File(Models.custom(c), Pf.SPEC).takeIf { it.isFile }
        val seen = Pf.screen(names, Pf.allowed(Manifest.all.map { it.file }.toSet(), spec?.readText()))
        if (!Pf.commit(seen)) return seen
        picks.filter { Pf.plain(it.name).let { n -> n != Pf.SPEC && n != Pf.MANIFEST } }.forEach { take(it, it.name!!, Pf.CAP) }
        st.update { it.copy(note = "Verifying", done = total, total = total) }
        val listed = Pf.manifest(File(stage, Pf.MANIFEST).readText())
        sums.remove(Pf.MANIFEST)
        val res = Pf.verify(sums, listed)
        if (Pf.commit(res)) install(c, stage, sums)
        return res
    }

    private fun meta(c: Context, u: Uri): Pick {
        var name: String? = null
        var size = -1L
        runCatching {
            c.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) {
                    name = it.getString(0)
                    size = if (it.isNull(1)) -1 else it.getLong(1)
                }
            }
        }
        return Pick(u, name, size)
    }

    private fun copy(c: Context, u: Uri, to: File, name: String, cap: Long, tick: (Long) -> Unit): String {
        val md = MessageDigest.getInstance("SHA-256")
        var n = 0L
        try {
            val input = c.contentResolver.openInputStream(u) ?: throw Bad(name, "could not be opened")
            input.use { i ->
                to.outputStream().buffered(1 shl 16).use { o ->
                    val b = ByteArray(1 shl 20)
                    while (true) {
                        val k = i.read(b)
                        if (k < 0) break
                        n += k
                        if (n > cap) throw Bad(name, "larger than ${Show.mb(cap)} MB")
                        md.update(b, 0, k)
                        o.write(b, 0, k)
                        tick(k.toLong())
                    }
                }
            }
        } catch (_: IOException) {
            throw Bad(name, "could not be copied, storage may be full")
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun move(a: File, b: File) {
        Files.move(a.toPath(), b.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    private fun install(c: Context, stage: File, sums: Map<String, String>) {
        val dir = Models.custom(c).also { it.mkdirs() }
        val old = File(stage.parentFile, "previous").also { it.deleteRecursively(); it.mkdirs() }
        val taken = ArrayList<String>()
        val put = ArrayList<String>()
        val order = sums.keys + Pf.MANIFEST
        try {
            order.forEach { n ->
                val d = File(dir, n)
                if (d.exists()) {
                    move(d, File(old, n))
                    taken += n
                }
                move(File(stage, n), d)
                put += n
            }
        } catch (e: IOException) {
            put.forEach { File(dir, it).delete() }
            taken.forEach { move(File(old, it), File(dir, it)) }
            throw Bad("Install", "could not move files, nothing changed")
        }
        sums.forEach { (n, s) -> Models.stamp(c, File(dir, n), s) }
    }
}
