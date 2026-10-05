package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SayStreamTest {
    private fun run(vararg chunks: String): Pair<String, Boolean> {
        val s = SayStream()
        return chunks.joinToString("") { s.feed(it) } to s.done()
    }

    private fun splits(text: String, at: List<Int>) = (listOf(0) + at + text.length).zipWithNext { a, b -> text.substring(a, b) }.toTypedArray()

    @Test
    fun `plain say is streamed`() {
        assertEquals("hello world" to true, run("""{"say":"hello world"}"""))
    }

    @Test
    fun `every single and double split gives the same text`() {
        val text = """{ "say" : "a\"b\\c\nd\t\/e é 😀 ₹5 A"}"""
        val want = "a\"b\\c\nd\t/e é 😀 ₹5 A"
        for (i in 0..text.length) assertEquals(want to true, run(*splits(text, listOf(i))), "split $i")
        for (i in 0..text.length step 3) for (j in i..text.length step 5) assertEquals(want to true, run(*splits(text, listOf(i, j))), "split $i $j")
        assertEquals(want to true, run(*text.map { it.toString() }.toTypedArray()))
    }

    @Test
    fun `chunks return only the new characters`() {
        val s = SayStream()
        assertEquals("", s.feed("{\"sa"))
        assertEquals("", s.feed("y\": \""))
        assertEquals("Hi", s.feed("Hi"))
        assertEquals("", s.feed("\\"))
        assertEquals("\n", s.feed("n"))
        assertEquals("", s.feed("\\u00"))
        assertEquals("é", s.feed("e9"))
        assertFalse(s.done())
        assertEquals("!", s.feed("!\"}"))
        assertTrue(s.done())
        assertEquals("", s.feed("more"))
    }

    @Test
    fun `a tool call yields nothing`() {
        assertEquals("" to false, run("""{"tool":"spend","args":{"query":"say"}}"""))
        assertEquals("" to false, run("{\"to", "ol\":\"x\",\"args\":{\"say\":\"no\"}}"))
        assertEquals("" to false, run("hello"))
    }

    @Test
    fun `an unfinished say is not done`() {
        assertEquals("par" to false, run("""{"say":"par"""))
        assertEquals("" to false, run("""{"say":"""))
    }

    @Test
    fun `an empty say is done`() {
        assertEquals("" to true, run("""{"say":""}"""))
    }

    @Test
    fun `a surrogate pair split across chunks stays whole`() {
        val s = SayStream()
        assertEquals("", s.feed("{\"say\":\"\uD83D"))
        assertEquals("😀", s.feed("\uDE00"))
    }
}
