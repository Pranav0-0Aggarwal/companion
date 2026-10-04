package app.companion.core

import java.util.Locale

class Line(val name: String, val ok: Boolean, val note: String)

object PrivateFiles {
    const val MANIFEST = "custom.json"
    const val SPEC = "model_spec.json"
    const val CAP = 2L shl 30
    const val SMALL = 1L shl 20
    const val MARGIN = 16L shl 20

    val IGNORED = setOf("TOKENIZER.md", "tokenizer.mbpe", "tokenizer_vectors.json", "SHA256SUMS")

    private val hex = Regex("[0-9a-f]{64}")

    fun plain(raw: String?): String? =
        raw?.takeIf { it.isNotEmpty() && it.length <= 128 && ".." !in it && it.none { c -> c == '/' || c == '\\' || c < ' ' } }

    fun shown(raw: String?): String = (raw ?: "unnamed").filter { it >= ' ' }.take(40)

    fun skip(name: String?) = plain(name)?.let { it in IGNORED } == true

    fun allowed(base: Set<String>, spec: String?): Set<String> =
        base + SPEC + MANIFEST + (spec?.let { runCatching { BertSpec.fromJson(it).let { s -> s.files + s.calibration } }.getOrNull() }.orEmpty())

    fun screen(raw: List<String?>, ok: Set<String>): List<Line> {
        val seen = HashSet<String>()
        val out = raw.map { r ->
            val n = plain(r)
            when {
                n == null -> Line(shown(r), false, "not a plain file name")
                n in IGNORED -> Line(n, true, "ignored, not copied")
                n !in ok -> Line(n, false, "not a model file name")
                !seen.add(n) -> Line(n, false, "picked twice")
                else -> Line(n, true, "accepted")
            }
        }
        return if (MANIFEST in seen) out else out + Line(MANIFEST, false, "pick custom.json too")
    }

    fun room(names: List<String?>, sizes: List<Long>, free: Long): List<Line> {
        val big = names.indices.filter { sizes[it] > CAP }.map { Line(shown(names[it]), false, "larger than ${CAP shr 30} GB") }
        val need = sizes.filter { it > 0 }.sum() + MARGIN
        return big + if (need > free) listOf(Line("Storage", false, "need ${Show.mbUp(need)} MB free, ${Show.mb(free)} MB available")) else emptyList()
    }

    fun manifest(text: String?): Map<String, String>? = runCatching {
        Json.obj(text!!).mapValues { (_, v) -> (v as String).lowercase(Locale.ROOT).also { require(hex.matches(it)) } }
    }.getOrNull()

    fun verify(sums: Map<String, String>, listed: Map<String, String>?): List<Line> {
        val head = if (listed == null) Line(MANIFEST, false, "not a map of file name to SHA-256") else Line(MANIFEST, true, "${listed.size} entries")
        return listOf(head) + sums.map { (n, s) ->
            val want = listed?.get(n)
            when {
                listed == null -> Line(n, false, "not checked")
                want == null && n == SPEC -> Line(n, true, "accepted")
                want == null -> Line(n, false, "not listed in custom.json")
                want != s -> Line(n, false, "checksum does not match custom.json")
                else -> Line(n, true, "verified")
            }
        }
    }

    fun commit(lines: List<Line>) = lines.isNotEmpty() && lines.all { it.ok }
}
