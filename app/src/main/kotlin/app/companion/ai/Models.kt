package app.companion.ai

import android.content.Context
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

class Spec(val name: String, val file: String, val sha: String, val bytes: Long, val idleMs: Long)

object Models {
    val decide = Spec("Decide", "decide.tflite", "", 0, 30_000)
    val needle = Spec("Needle 3", "needle3.bin", "", 0, 30_000)
    const val TOKENIZER = "tokenizer.json"
    const val SCHEMA = "schema_prefix.json"

    fun dir(c: Context) = File(c.filesDir, "models").also { it.mkdirs() }

    fun file(c: Context, s: Spec) = File(dir(c), s.file)

    fun asset(c: Context, name: String) = File(dir(c), name)

    fun installed(c: Context, s: Spec) = s.sha.isNotEmpty() && file(c, s).let { it.isFile && verified(c, s, it) }

    private fun hex(i: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val b = ByteArray(1 shl 20)
        while (true) {
            val n = i.read(b)
            if (n < 0) break
            md.update(b, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha(f: File) = f.inputStream().use(::hex)

    private fun verified(c: Context, s: Spec, f: File): Boolean {
        val prefs = c.getSharedPreferences("models", Context.MODE_PRIVATE)
        val stamp = "${f.length()}:${f.lastModified()}"
        if (prefs.getString(s.file, null) == stamp) return true
        val ok = sha(f) == s.sha
        if (ok) prefs.edit().putString(s.file, stamp).apply() else prefs.edit().remove(s.file).apply()
        return ok
    }

    fun install(c: Context, s: Spec, src: InputStream): Boolean {
        val tmp = File(dir(c), "${s.file}.part")
        src.use { i -> tmp.outputStream().use { o -> i.copyTo(o, 1 shl 20) } }
        if (s.sha.isEmpty() || sha(tmp) != s.sha) {
            tmp.delete()
            return false
        }
        return tmp.renameTo(file(c, s))
    }
}
