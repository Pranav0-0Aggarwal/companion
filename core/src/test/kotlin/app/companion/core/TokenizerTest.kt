package app.companion.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    private fun dtk(unk: Int, vararg v: Pair<String, Double>): ByteArray {
        val b = java.io.ByteArrayOutputStream()
        val w = java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.write("DTK1".toByteArray())
        b.write(w.putInt(v.size).putInt(unk).array(), 0, 8)
        for ((p, s) in v) {
            val e = p.toByteArray()
            b.write(java.nio.ByteBuffer.allocate(10).order(java.nio.ByteOrder.LITTLE_ENDIAN).putDouble(s).putShort(e.size.toShort()).array())
            b.write(e)
        }
        return b.toByteArray()
    }

    @Test
    fun `word encoding is nfc and keeps compatibility forms`() {
        val t = SpTokenizer(listOf("[UNK]", "▁café", "▁ｈ"), listOf(-100f, -1f, -1f), 0)
        assertContentEquals(intArrayOf(1), t.word("cafe\u0301"))
        assertContentEquals(intArrayOf(2), t.word("ｈ"))
        assertContentEquals(intArrayOf(0), t.word("zz"))
    }

    @Test
    fun `loads from dtk`() {
        val t = SpTokenizer.fromDtk(dtk(0, "[UNK]" to -100.0, "▁a" to -1.0, "b" to -1.0))
        assertContentEquals(intArrayOf(1, 2), t.encode("ab"))
    }

    @Test
    fun `dtk rejects bad magic truncation and bad unk`() {
        val good = dtk(0, "[UNK]" to -100.0, "▁a" to -1.0)
        assertFailsWith<Exception> { SpTokenizer.fromDtk(good.copyOf(good.size - 3)) }
        assertFailsWith<Exception> { SpTokenizer.fromDtk(good.also { it[0] = 'X'.code.toByte() }) }
        assertFailsWith<Exception> { SpTokenizer.fromDtk(dtk(5, "[UNK]" to -1.0)) }
    }
}
