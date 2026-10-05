package app.companion.core

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun <T> sync(block: suspend () -> T): T {
    var r: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { r = it })
    return r!!.getOrThrow()
}

class DecodeTest {
    private val reg = chatDemo()

    private fun decode(vararg outs: String?): Pair<Draft?, List<Boolean>> {
        val modes = mutableListOf<Boolean>()
        val d = sync { Decode.run(reg) { strict -> modes += strict; outs[modes.size - 1] } }
        return d to modes
    }

    @Test
    fun `a valid free answer is taken without the grammar`() {
        val (d, modes) = decode("""  {"tool":"end_trip","args":{}} """, "unused")
        assertIs<Step.Call>(d!!.step)
        assertEquals("""{"tool":"end_trip","args":{}}""", d.raw)
        assertEquals(listOf(false), modes)
        val (s, m) = decode("""{"say":"hi"}""")
        assertEquals(Step.Say("hi"), s!!.step)
        assertEquals(listOf(false), m)
    }

    @Test
    fun `an unparseable free answer retries once with the grammar`() {
        for (bad in listOf("hello", """{"tool":"log_weight","args":{"weight":70}}""", """{"tool":"nope"}""", """{"tool":"end_trip"} {"say":"x"}""", """{"tool":"end_trip""", "")) {
            val (d, modes) = decode(bad, """{"tool":"log_weight","args":{"kg":70}}""")
            assertEquals(listOf(false, true), modes, bad)
            assertEquals(mapOf("kg" to 70.0), assertIs<Step.Call>(d!!.step).args, bad)
        }
    }

    @Test
    fun `the grammar answer is final even when it is bad`() {
        val (d, modes) = decode("x", "still not json")
        assertIs<Step.Bad>(d!!.step)
        assertEquals(listOf(false, true), modes)
    }

    @Test
    fun `a failed generation is not retried`() {
        val (d, modes) = decode(null, """{"say":"x"}""")
        assertNull(d)
        assertEquals(listOf(false), modes)
        val (e, m) = decode("bad", null)
        assertNull(e)
        assertEquals(listOf(false, true), m)
    }

    @Test
    fun `the object end is found once and ignores braces in strings`() {
        val e = ObjEnd()
        assertFalse(e.feed("{\"say\":\"a } b \\\" }"))
        assertFalse(e.feed("\""))
        assertTrue(e.feed("}"))
        assertTrue(e.done())
        assertTrue(e.feed("{{}}"))
    }

    @Test
    fun `the object end handles nesting and the forced lead`() {
        val e = ObjEnd()
        assertFalse(e.feed(Decode.LEAD))
        assertFalse(e.feed("tool\":\"log_meal\",\"args\":{\"items\":[{\"name\":\"d\"}"))
        assertFalse(e.feed("]}"))
        assertTrue(e.feed("}  trailing"))
        val x = ObjEnd()
        assertFalse(x.feed("}} text "))
        assertTrue(x.feed("{}"))
    }

    @Test
    fun `chunk boundaries never change where the object ends`() {
        val text = """{"say":"q\\\"}{","n":{"a":[1,{"b":"}"}]}}rest"""
        val want = text.indexOf("}rest") + 1
        for (i in 0..text.length) {
            val e = ObjEnd()
            val n = if (e.feed(text.substring(0, i))) i else if (e.feed(text.substring(i))) text.length else -1
            assertTrue(n >= want, "split $i")
        }
        val one = ObjEnd()
        var at = -1
        text.forEachIndexed { i, c -> if (at < 0 && one.feed(c.toString())) at = i + 1 }
        assertEquals(want, at)
    }

    @Test
    fun `the forced lead streams the say text and the plain format too`() {
        val s = SayStream()
        assertEquals("", s.feed(Decode.LEAD))
        assertEquals("", s.feed("say\":"))
        assertEquals("Hi there", s.feed(" \"Hi there"))
        assertEquals("!", s.feed("!\"}"))
        assertTrue(s.done())
        val t = SayStream()
        t.feed(Decode.LEAD)
        assertEquals("" to false, t.feed("tool\":\"spend\",\"args\":{\"say\":\"x\"}}") to t.done())
    }
}
