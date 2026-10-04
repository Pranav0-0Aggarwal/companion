package app.companion.core

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BpeTest {
    private val raw = ClassLoader.getSystemResourceAsStream("bert/tokenizer.json")!!.readBytes()
    private val bpe = Bpe.fromJson(String(raw, Charsets.UTF_8))
    private val vectors = (Json.obj(String(ClassLoader.getSystemResourceAsStream("bert/vectors.json")!!.readBytes(), Charsets.UTF_8))["vectors"] as List<*>).map { it as Map<*, *> }

    private val v2 = (Json.obj(String(ClassLoader.getSystemResourceAsStream("bert/vectors_v2.json")!!.readBytes(), Charsets.UTF_8))["vectors"] as List<*>).map { it as Map<*, *> }

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

    @Test
    fun `ids equal the reference for the 200 synthetic vectors of the private tokenizer`() {
        assertEquals(200, v2.size)
        for (v in v2) assertEquals(ints(v["input_ids"]).drop(1).dropLast(1).take(126), bpe.encode(v["input"] as String, 126).toList(), "ids for ${v["input"]}")
    }

    @Test
    fun `merges written as pairs and a truncation block encode the same`() {
        val m = Json.obj(String(raw, Charsets.UTF_8))
        val model = m["model"] as Map<*, *>
        val pairs = (model["merges"] as List<*>).map { (it as String).split(' ') }
        val text = Json.write(m + ("model" to model + ("merges" to pairs)) + ("truncation" to mapOf("direction" to "Right", "max_length" to 128)))
        val b = Bpe.fromJson(text)
        for (v in vectors + v2) {
            val t = (v["text"] ?: v["input"]) as String
            assertEquals(bpe.encode(t).toList(), b.encode(t).toList(), t)
        }
    }

    @Test
    fun `runs of spaces are single added tokens matched longest first`() {
        val sp = bpe.id(" ".repeat(24))!!
        assertEquals(listOf(sp), bpe.encode(" ".repeat(24)).toList())
        for (n in 2..24) assertEquals(listOf(sp + 24 - n), bpe.encode(" ".repeat(n)).toList(), "run $n")
        assertEquals(listOf(sp, sp + 18), bpe.encode(" ".repeat(30)).toList())
        assertEquals(bpe.encode("a").toList() + (sp + 22) + bpe.encode("b").toList(), bpe.encode("a  b").toList())
        assertEquals(listOf(sp) + bpe.encode(" word").toList(), bpe.encode(" ".repeat(25) + "word").toList())
    }

    @Test
    fun `literal special markers are one token and mask strips the space before it`() {
        for ((s, id) in listOf("[CLS]" to 50281, "[SEP]" to 50282, "[MASK]" to 50284, "[PAD]" to 50283, "[UNK]" to 50280, "<|endoftext|>" to 50279)) {
            assertEquals(listOf(id), bpe.encode(s).toList(), s)
        }
        val a = bpe.encode("a").single()
        assertEquals(listOf(a, 50284), bpe.encode("a   [MASK]").toList())
        assertEquals(listOf(a, 50281, a), bpe.encode("a[CLS]a").toList())
    }

    @Test
    fun `text is normalised to NFC before it is split`() {
        assertEquals(bpe.encode("caf\u00e9").toList(), bpe.encode("cafe\u0301").toList())
        assertEquals(bpe.encode("\u00c5").toList(), bpe.encode("A\u030a").toList())
    }
}
