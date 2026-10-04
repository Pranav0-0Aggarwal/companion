package app.companion.core

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BpeTest {
    private val raw = ClassLoader.getSystemResourceAsStream("bert/tokenizer.json")!!.readBytes()
    private val bpe = Bpe.fromJson(String(raw, Charsets.UTF_8))
    private val vectors = (Json.obj(String(ClassLoader.getSystemResourceAsStream("bert/vectors.json")!!.readBytes(), Charsets.UTF_8))["vectors"] as List<*>).map { it as Map<*, *> }

    private fun ints(v: Any?) = (v as List<*>).map { (it as Number).toInt() }

    @Test
    fun `tokenizer file is the public ModernBERT large one`() {
        val sha = MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it) }
        assertEquals("9fd55248d51d33976b324fc11592e28071da7d41e0e9401dfb7082e30574b7b1", sha)
    }

    @Test
    fun `ids equal the huggingface tokenizers reference for every vector`() {
        assertTrue(vectors.size >= 200)
        for (v in vectors) assertEquals(ints(v["ids"]), bpe.encode(v["text"] as String).toList(), "ids for ${v["text"]}")
    }

    @Test
    fun `a limit keeps the leading ids of the full encoding`() {
        for (v in vectors) {
            val full = ints(v["ids"])
            for (n in listOf(0, 1, 5, 126)) assertEquals(full.take(n), bpe.encode(v["text"] as String, n).toList(), "limit $n for ${v["text"]}")
        }
    }

    @Test
    fun `special and added token ids come from the tokenizer file`() {
        assertEquals(50281, bpe.id("[CLS]"))
        assertEquals(50282, bpe.id("[SEP]"))
        assertEquals(50283, bpe.id("[PAD]"))
        assertEquals(listOf(50276), bpe.encode("  ").toList())
        assertEquals(listOf(50254, bpe.encode(" ").single()), bpe.encode(" ".repeat(25)).toList())
    }
}
