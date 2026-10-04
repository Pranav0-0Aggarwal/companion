package app.companion.core

import java.text.Normalizer
import java.util.regex.Pattern

class Bpe private constructor(
    private val vocab: Map<String, Int>,
    private val rank: Map<Long, Int>,
    private val merged: IntArray,
    private val raw: Map<Char, List<Added>>,
    private val norm: Map<Char, List<Added>>,
    private val nfc: Boolean,
) {
    class Added(val text: String, val id: Int, val lstrip: Boolean, val rstrip: Boolean)

    private val bytes = IntArray(256) { vocab[CHARS[it].toString()] ?: -1 }

    fun id(token: String): Int? = vocab[token] ?: (raw.values + norm.values).flatten().firstOrNull { it.text == token }?.id

    fun encode(text: String, limit: Int = Int.MAX_VALUE): IntArray {
        val out = ArrayList<Int>()
        scan(text, raw, { s ->
            if (out.size < limit) scan(if (nfc) Normalizer.normalize(s, Normalizer.Form.NFC) else s, norm, { w -> words(w, out, limit) }, { if (out.size < limit) out.add(it.id) })
        }, { if (out.size < limit) out.add(it.id) })
        return if (out.size > limit) out.subList(0, limit).toIntArray() else out.toIntArray()
    }

    private fun scan(s: String, toks: Map<Char, List<Added>>, plain: (String) -> Unit, hit: (Added) -> Unit) {
        var from = 0
        var i = 0
        while (i < s.length) {
            val t = toks[s[i]]?.firstOrNull { s.startsWith(it.text, i) }
            if (t == null) {
                i++
                continue
            }
            var a = i
            var b = i + t.text.length
            if (t.lstrip) while (a > from && white(s[a - 1])) a--
            if (t.rstrip) while (b < s.length && white(s[b])) b++
            if (a > from) plain(s.substring(from, a))
            hit(t)
            from = b
            i = b
        }
        if (from < s.length) plain(s.substring(from))
    }

    private fun words(s: String, out: MutableList<Int>, limit: Int) {
        val m = SPLIT.matcher(s)
        while (out.size < limit && m.find()) word(m.group(), out)
    }

    private fun word(w: String, out: MutableList<Int>) {
        val b = w.toByteArray(Charsets.UTF_8)
        val ids = IntArray(b.size) { bytes[b[it].toInt() and 0xff] }
        var n = ids.size
        while (n > 1) {
            var best = Int.MAX_VALUE
            var at = -1
            for (j in 0 until n - 1) {
                val r = rank[key(ids[j], ids[j + 1])] ?: continue
                if (r < best) {
                    best = r
                    at = j
                }
            }
            if (at < 0) break
            ids[at] = merged[best]
            System.arraycopy(ids, at + 2, ids, at + 1, n - at - 2)
            n--
        }
        for (j in 0 until n) out.add(ids[j])
    }

    companion object {
        private const val WS = "\\t\\n\\x0B\\f\\r \\x85\\xA0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000"
        private val SPLIT = Pattern.compile("'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^$WS\\p{L}\\p{N}]+|[$WS]+(?![^$WS])|[$WS]+")
        private val CHARS = chars()

        private fun chars(): CharArray {
            val keep = (33..126) + (161..172) + (174..255)
            val out = CharArray(256)
            var extra = 0
            for (b in 0..255) out[b] = if (b in keep) b.toChar() else (256 + extra++).toChar()
            return out
        }

        private fun white(c: Char) = c in "\t\n\u000B\u000C\r \u0085      　" || c in ' '..' '

        private fun key(a: Int, b: Int) = (a.toLong() shl 32) or b.toLong()

        private fun table(a: List<Added>) = a.sortedByDescending { it.text.length }.groupBy { it.text[0] }

        fun fromJson(text: String): Bpe {
            val m = Json.obj(text)
            val nz = (m["normalizer"] as? Map<*, *>)?.get("type")
            require(m["normalizer"] == null || nz == "NFC")
            val pre = m["pre_tokenizer"] as Map<*, *>
            require(pre["type"] == "ByteLevel" && pre["add_prefix_space"] == false && pre["use_regex"] == true)
            val model = m["model"] as Map<*, *>
            require(model["type"] == "BPE" && model["ignore_merges"] != true && model["dropout"] == null)
            val vocab = (model["vocab"] as Map<*, *>).entries.associate { (k, v) -> k as String to (v as Number).toInt() }
            val pairs = (model["merges"] as List<*>).map { e ->
                val p = if (e is List<*>) e.map { it as String } else (e as String).split(' ')
                require(p.size == 2)
                p
            }
            val rank = HashMap<Long, Int>(pairs.size * 2)
            val merged = IntArray(pairs.size)
            pairs.forEachIndexed { i, (a, b) ->
                rank.putIfAbsent(key(vocab.getValue(a), vocab.getValue(b)), i)
                merged[i] = vocab.getValue(a + b)
            }
            val added = (m["added_tokens"] as List<*>).map { t ->
                t as Map<*, *>
                require(t["single_word"] != true)
                (t["normalized"] == true) to Added(t["content"] as String, (t["id"] as Number).toInt(), t["lstrip"] == true, t["rstrip"] == true)
            }
            return Bpe(vocab, rank, merged, table(added.filter { !it.first }.map { it.second }), table(added.filter { it.first }.map { it.second }), nz == "NFC")
        }
    }
}
