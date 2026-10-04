package app.companion.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.Normalizer

class SpTokenizer(private val pieces: List<String>, private val scores: List<Float>, val unk: Int) {
    private val index = HashMap<String, Int>().also { m -> pieces.forEachIndexed { i, p -> m.putIfAbsent(p, i) } }
    private val maxLen = pieces.maxOfOrNull { it.codePointCount(0, it.length) } ?: 1
    private val unkScore = (scores.minOrNull() ?: 0f) - 10f
    private val spaces = Regex("\\s+")

    private fun prepare(text: String): IntArray {
        val n = Normalizer.normalize(text, Normalizer.Form.NFKC).replace(spaces, " ").trim()
        return ("▁" + n.replace(' ', '▁')).codePoints().toArray()
    }

    fun encode(text: String): IntArray = merge(viterbi(prepare(text)))

    fun word(w: String): IntArray = merge(viterbi(("▁" + Normalizer.normalize(w, Normalizer.Form.NFC)).codePoints().toArray()))

    private fun viterbi(cp: IntArray): IntArray {
        val n = cp.size
        val best = FloatArray(n + 1) { Float.NEGATIVE_INFINITY }
        val from = IntArray(n + 1)
        val id = IntArray(n + 1)
        best[0] = 0f
        for (i in 0 until n) {
            if (best[i] == Float.NEGATIVE_INFINITY) continue
            val sb = StringBuilder()
            for (len in 1..minOf(maxLen, n - i)) {
                sb.appendCodePoint(cp[i + len - 1])
                val p = index[sb.toString()] ?: continue
                val s = best[i] + scores[p]
                if (s > best[i + len]) {
                    best[i + len] = s
                    from[i + len] = i
                    id[i + len] = p
                }
            }
            val u = best[i] + unkScore
            if (u > best[i + 1]) {
                best[i + 1] = u
                from[i + 1] = i
                id[i + 1] = unk
            }
        }
        val out = ArrayDeque<Int>()
        var at = n
        while (at > 0) {
            out.addFirst(id[at])
            at = from[at]
        }
        return out.toIntArray()
    }

    private fun merge(ids: IntArray): IntArray {
        val out = ArrayList<Int>(ids.size)
        for (t in ids) if (!(t == unk && out.lastOrNull() == unk)) out.add(t)
        return out.toIntArray()
    }

    companion object {
        fun fromDtk(b: ByteArray): SpTokenizer {
            val m = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
            require(b.size >= 12 && String(b, 0, 4, Charsets.US_ASCII) == "DTK1")
            m.position(4)
            val n = m.int
            val unk = m.int
            require(n in 1..MAX && unk in 0 until n)
            val pieces = ArrayList<String>(n)
            val scores = ArrayList<Float>(n)
            repeat(n) {
                scores.add(m.double.toFloat())
                val a = ByteArray(m.short.toInt() and 0xffff)
                m.get(a)
                pieces.add(String(a, Charsets.UTF_8))
            }
            return SpTokenizer(pieces, scores, unk)
        }

        private const val MAX = 1_000_000

        fun fromJson(text: String): SpTokenizer {
            val m = Json.obj(text)
            val vocab = (m["vocab"] as List<*>).map { it as List<*> }
            return SpTokenizer(
                vocab.map { it[0] as String },
                vocab.map { (it[1] as Number).toFloat() },
                (m["unk_id"] as Number).toInt(),
            )
        }
    }
}
