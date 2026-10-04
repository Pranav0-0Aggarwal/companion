package app.companion.ai

import android.content.Context
import app.companion.core.Calibration
import app.companion.core.Hash
import app.companion.core.Json
import java.io.File

class Spec(val name: String, val file: String, val bytes: Long, val sha: String, val idleMs: Long = 0, val tag: String = "models-v1")

object Manifest {
    private const val HOST = "https://github.com/Pranav0-0Aggarwal/companion/releases/download/"

    fun url(s: Spec) = "$HOST${s.tag}/${s.file}"

    val decide = Spec("Decide", "decide.tflite", 518772848, "9f7655625d6861ee22792fca56d9fa7291192ec5f893f6c169b8e7dd69ae2072", 30_000)
    val tokenizer = Spec("Tokenizer", "tokenizer.dtk", 2354330, "746e5a467d4019afbbef1c945c8fbe4fb2b4a9da5401a3cb8526ab19cb57d0fa")
    val schema = Spec("Schema", "schema_prefix.json", 3396, "58d7211b245e5a45260190b4edb5d29df62c0d7897e6d0359f8a8a566c4140d5")
    val calibration = Spec("Calibration", "calibration.json", 938, "37e2bcaf993b8a44b0c87f6391ef1a45c9a4fff00991cb09ae4dd12380ca0ae1")
    val needle = Spec("Needle 3", "needle3.cact", 35335380, "c9d915eca282ed42d1a09b143b592adb4cc6744ffe2d294adf5cfc5548170c38", 60_000)
    val nux = Spec("Smart extraction (NuExtract)", "nuextract-tiny-q4_0.gguf", 352_154_432, "cb10c8078f425cdba1040246712b47fd33af083d2db7af6ac63553677bf577e7", 60_000, "models-v2")
    val all = listOf(needle, tokenizer, schema, calibration, decide, nux)
    val wanted get() = all.filter { it !== nux || Chip.nux }
}

enum class Have { No, Base, Custom }

object Models {
    private val hex = Regex("[0-9a-f]{64}")

    fun pinned(s: Spec) = s.bytes > 0 && hex.matches(s.sha)

    fun dir(c: Context) = File(c.filesDir, "models").also { it.mkdirs() }

    fun custom(c: Context) = File(dir(c), "custom")

    fun base(c: Context, s: Spec) = File(dir(c), s.file)

    fun part(c: Context, s: Spec) = File(File(c.noBackupFilesDir, "models").also { it.mkdirs() }, "${s.file}.part")

    private var listed: Pair<String, Map<String, String>>? = null

    @Synchronized
    private fun listing(c: Context): Map<String, String> {
        val f = File(custom(c), "custom.json")
        if (!f.isFile) return emptyMap()
        val stamp = "${f.length()}:${f.lastModified()}"
        listed?.takeIf { it.first == stamp }?.let { return it.second }
        val m = runCatching {
            Json.obj(f.readText()).mapNotNull { (k, v) -> (v as? String)?.lowercase()?.takeIf { hex.matches(it) && Manifest.all.any { s -> s.file == k } }?.let { k to it } }.toMap()
        }.getOrDefault(emptyMap())
        listed = stamp to m
        return m
    }

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

    fun have(c: Context, s: Spec): Have {
        listing(c)[s.file]?.let { sha -> if (verified(c, File(custom(c), s.file), sha)) return Have.Custom }
        return if (pinned(s) && verified(c, base(c, s), s.sha)) Have.Base else Have.No
    }

    fun file(c: Context, s: Spec): File? = when (have(c, s)) {
        Have.Custom -> File(custom(c), s.file)
        Have.Base -> base(c, s)
        Have.No -> null
    }

    fun sha(c: Context, s: Spec): String? = when (have(c, s)) {
        Have.Custom -> listing(c)[s.file]
        Have.Base -> s.sha
        Have.No -> null
    }

    fun version(c: Context, s: Spec): String? = when (have(c, s)) {
        Have.Custom -> "${listing(c)[s.file]?.take(8)} custom"
        Have.Base -> s.sha.take(8)
        Have.No -> null
    }

    private var cal: Pair<String, Calibration>? = null

    @Synchronized
    fun loadCalibration(c: Context): Calibration {
        val f = file(c, Manifest.calibration) ?: return Calibration.DEFAULT
        val stamp = "${f.path}:${f.length()}:${f.lastModified()}"
        cal?.takeIf { it.first == stamp }?.let { return it.second }
        return runCatching { Calibration.fromJson(f.readText()) }.getOrDefault(Calibration.DEFAULT).also { cal = stamp to it }
    }
}
