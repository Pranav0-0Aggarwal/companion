package app.companion.ai

import android.content.Context
import app.companion.core.Hash
import java.io.File

class Spec(val name: String, val file: String, val bytes: Long, val sha: String, val tag: String, val idleMs: Long = 0)

object Manifest {
    private const val HOST = "https://github.com/Pranav0-0Aggarwal/companion/releases/download/"

    fun url(s: Spec) = "$HOST${s.tag}/${s.file}"

    val spec = Spec("ModernBERT spec", "model_spec.json", 7162, "38ed5c9d275d37b73d741f9b475410c1d5a1c316a15675fd9483ceddd24609ea", "models-v4")
    val tokenizer = Spec("ModernBERT tokenizer", "tokenizer.json", 3583326, "fe530b837c912faf33acd6b1a15a46234259519acb967ead693c692cc2e93647", "models-v4")
    val calibration = Spec("ModernBERT calibration", "calibration.json", 950, "e6f811eff7bcbdf010745ccc5266f225baeb79574bf866d4f62252a602b5f7b7", "models-v4")
    val type = Spec("ModernBERT type", "type.tflite", 409130000, "df080d4697ed36a212095cf20b44c03c24ff937d6194eafe92ad9c8eac1ff821", "models-v4", 30_000)
    val nux = Spec("Smart extraction (NuExtract)", "nuextract-tiny-q4_0.gguf", 352_154_432, "cb10c8078f425cdba1040246712b47fd33af083d2db7af6ac63553677bf577e7", "models-v2", 60_000)
    val chat = Spec("Conversation (Qwen3.5-2B)", "qwen3.5-2b-q4_0.gguf", 1_236_740_608, "b58f077d816cc565b2d6ae55e2da5997b3837a1bd79ec70a014801c582fa627b", "models-v3", 60_000)
    val bert = listOf(spec, tokenizer, calibration, type)
    val all = bert + listOf(nux, chat)
    val llama = setOf(nux, chat)
    val wanted get() = all.filter { it !in llama || Chip.nux }
}

object Models {
    private val hex = Regex("[0-9a-f]{64}")
    private val gliner = listOf("decide.tflite", "tokenizer.dtk", "schema_prefix.json", "calibration.json")

    fun pinned(s: Spec) = s.bytes > 0 && hex.matches(s.sha)

    fun dir(c: Context) = File(c.filesDir, "models").also { it.mkdirs() }

    private fun parts(c: Context) = File(c.noBackupFilesDir, "models")

    private fun retire(c: Context, flag: String, names: List<String>, vararg more: File) {
        val prefs = c.getSharedPreferences("models", Context.MODE_PRIVATE)
        if (prefs.getBoolean(flag, false)) return
        val part = parts(c)
        names.forEach { n -> listOf(File(dir(c), n), File(part, "$n.part"), File(part, "$n.part.ranges"), File(part, "$n.part.ranges.tmp")).forEach(File::delete) }
        more.forEach(File::deleteRecursively)
        prefs.edit().apply {
            prefs.all.keys.filter { k -> names.any(k::endsWith) }.forEach(::remove)
            putBoolean(flag, true)
        }.apply()
    }

    fun purge(c: Context) {
        retire(c, "needle_purged", listOf("needle3.cact"))
        retire(c, "gliner_purged", gliner, File(dir(c), "custom"), File(parts(c), "import"), File(parts(c), "previous"))
    }

    fun base(c: Context, s: Spec) = File(dir(c), s.file)

    fun part(c: Context, s: Spec) = File(parts(c).also { it.mkdirs() }, "${s.file}.part")

    internal fun verified(c: Context, f: File, sha: String): Boolean {
        if (!f.isFile) return false
        val prefs = c.getSharedPreferences("models", Context.MODE_PRIVATE)
        val stamp = "${f.length()}:${f.lastModified()}:$sha"
        if (prefs.getString(f.path, null) == stamp) return true
        val ok = Hash.sha256(f) == sha
        prefs.edit().apply { if (ok) putString(f.path, stamp) else remove(f.path) }.apply()
        return ok
    }

    fun stamp(c: Context, f: File, sha: String) {
        c.getSharedPreferences("models", Context.MODE_PRIVATE).edit().putString(f.path, "${f.length()}:${f.lastModified()}:$sha").apply()
    }

    fun has(c: Context, s: Spec) = pinned(s) && verified(c, base(c, s), s.sha)

    fun file(c: Context, s: Spec): File? = base(c, s).takeIf { has(c, s) }

    fun sha(c: Context, s: Spec): String? = s.sha.takeIf { has(c, s) }

    fun version(c: Context, s: Spec): String? = sha(c, s)?.take(8)
}
