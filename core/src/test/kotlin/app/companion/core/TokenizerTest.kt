package app.companion.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TokenizerTest {
    private val vocab = listOf(
        "[UNK]" to -100f, "▁" to -4f, "▁hello" to -2f, "▁hel" to -3f, "lo" to -3f, "▁world" to -2.5f,
        "▁w" to -4f, "orld" to -3f, "▁rs" to -3f, "1" to -5f, "2" to -5f,
    )
    private val tok = SpTokenizer(vocab.map { it.first }, vocab.map { it.second }, 0)

    @Test
    fun `viterbi prefers the best scoring split`() {
        assertContentEquals(intArrayOf(2, 5), tok.encode("hello world"))
    }

    @Test
    fun `whitespace is collapsed and text is nfkc normalised`() {
        assertContentEquals(tok.encode("hello world"), tok.encode("  hello \n\t world "))
        assertContentEquals(tok.encode("hello"), tok.encode("ｈｅｌｌｏ"))
    }

    @Test
    fun `unknown runs collapse to a single unk`() {
        assertContentEquals(intArrayOf(2, 1, 0), tok.encode("hello zz"))
    }

    @Test
    fun `digits fall back to pieces`() {
        assertContentEquals(intArrayOf(8, 9, 10), tok.encode("rs12").let { intArrayOf(it[0], it[1], it[2]) })
    }

    @Test
    fun `loads from json`() {
        val t = SpTokenizer.fromJson("""{"unk_id": 0, "vocab": [["[UNK]", -100], ["▁a", -1], ["b", -1]]}""")
        assertContentEquals(intArrayOf(1, 2), t.encode("ab"))
    }

    @Test
    fun `parity with the exported vectors when present`() {
        val dir = File("../models")
        val vectors = File(dir, "tokenizer_vectors.json")
        val asset = dir.listFiles { f -> f.name.startsWith("tokenizer") && f.name.endsWith(".json") && f.name != vectors.name }?.firstOrNull()
        if (!vectors.exists() || asset == null) return
        val t = SpTokenizer.fromJson(asset.readText())
        val cases = Json.parse(vectors.readText()) as List<*>
        for (c in cases) {
            val m = c as Map<*, *>
            val want = (m["ids"] as List<*>).map { (it as Number).toInt() }
            assertEquals(want, t.encode(m["text"] as String).toList(), "text=${m["text"]}")
        }
    }
}
