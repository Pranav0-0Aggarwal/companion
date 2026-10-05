package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private sealed interface N {
    data class Lit(val s: String) : N
    data class Cls(val neg: Boolean, val r: List<CharRange>) : N
    data class Ref(val n: String) : N
    data class Seq(val l: List<N>) : N
    data class Alt(val l: List<N>) : N
    data class Rep(val n: N, val min: Int, val max: Int) : N
}

private class Gbnf(text: String) {
    val rules = HashMap<String, N>()

    init {
        for (line in text.lines()) {
            val (name, body) = line.split(" ::= ", limit = 2)
            rules[name] = P(body).alt()
        }
    }

    private class P(val s: String) {
        var i = 0

        fun alt(): N {
            val a = mutableListOf(seq())
            while (i < s.length && s[i] == '|') {
                i++
                a += seq()
            }
            return if (a.size == 1) a[0] else N.Alt(a)
        }

        fun seq(): N {
            val l = mutableListOf<N>()
            while (true) {
                while (i < s.length && s[i] == ' ') i++
                if (i >= s.length || s[i] == '|' || s[i] == ')') return N.Seq(l)
                l += rep()
            }
        }

        fun rep(): N {
            var n = atom()
            while (i < s.length) {
                n = when (s[i]) {
                    '?' -> N.Rep(n, 0, 1)
                    '*' -> N.Rep(n, 0, Int.MAX_VALUE)
                    '+' -> N.Rep(n, 1, Int.MAX_VALUE)
                    '{' -> {
                        val e = s.indexOf('}', i)
                        val p = s.substring(i + 1, e).split(',')
                        i = e
                        N.Rep(n, p[0].toInt(), if (p.size > 1) p[1].toInt() else p[0].toInt())
                    }
                    else -> return n
                }
                i++
            }
            return n
        }

        fun ch(): Char {
            val c = s[i++]
            if (c != '\\') return c
            return when (val e = s[i++]) {
                'x' -> s.substring(i, i + 2).toInt(16).toChar().also { i += 2 }
                'n' -> '\n'
                else -> e
            }
        }

        fun atom(): N = when (s[i]) {
            '"' -> {
                i++
                val sb = StringBuilder()
                while (s[i] != '"') sb.append(ch())
                i++
                N.Lit(sb.toString())
            }
            '[' -> {
                i++
                val neg = s[i] == '^'
                if (neg) i++
                val r = mutableListOf<CharRange>()
                while (s[i] != ']') {
                    val a = ch()
                    if (s[i] == '-' && s[i + 1] != ']') {
                        i++
                        r += a..ch()
                    } else {
                        r += a..a
                    }
                }
                i++
                N.Cls(neg, r)
            }
            '(' -> {
                i++
                alt().also { i++ }
            }
            else -> {
                val st = i
                while (i < s.length && (s[i].isLowerCase() || s[i].isDigit() || s[i] == '-')) i++
                N.Ref(s.substring(st, i))
            }
        }
    }

    fun matches(t: String) = m(N.Ref("root"), t, setOf(0)).contains(t.length)

    private fun m(n: N, t: String, p: Set<Int>): Set<Int> = when (n) {
        is N.Lit -> p.filter { t.startsWith(n.s, it) }.map { it + n.s.length }.toSet()
        is N.Cls -> p.filter { it < t.length && (n.r.any { r -> t[it] in r } != n.neg) }.map { it + 1 }.toSet()
        is N.Ref -> m(rules.getValue(n.n), t, p)
        is N.Seq -> n.l.fold(p) { a, x -> m(x, t, a) }
        is N.Alt -> n.l.flatMapTo(HashSet()) { m(it, t, p) }
        is N.Rep -> {
            var cur = p
            val acc = HashSet<Int>().apply { if (n.min == 0) addAll(p) }
            var k = 0
            while (k < n.max && cur.isNotEmpty()) {
                cur = m(n.n, t, cur)
                k++
                if (k >= n.min && !acc.addAll(cur)) break
            }
            acc
        }
    }
}

class GrammarTest {
    private val text = chatDemo().grammar()
    private val g = Gbnf(text)

    private fun ok(s: String) = assertTrue(g.matches(s), s)

    private fun no(s: String) = assertFalse(g.matches(s), s)

    @Test
    fun `the grammar has the expected rules and literals`() {
        val lines = text.lines()
        assertEquals("root ::= call | say", lines[0])
        assertTrue(lines[1].startsWith("call ::= c-spend | c-log-meal | c-log-weight | c-end-trip | c-opts | c-count"))
        assertTrue(""""\"breakfast\"" | "\"lunch\"" | "\"snacks\"" | "\"dinner\""""" in text)
        assertTrue("ws ::= [ ]?" in lines)
        assertTrue(lines.all { Regex("[a-z][a-z0-9-]* ::= .+").matches(it) })
    }

    @Test
    fun `every referenced rule is defined`() {
        val refs = Regex("""(?<![\w"\\-])[a-z][a-z0-9-]*(?![\w"-])""")
        val defined = g.rules.keys
        val body = text.lines().joinToString("\n") { it.substringAfter(" ::= ") }.replace(Regex(""""(\\.|[^"\\])*""""), "").replace(Regex("\\[(\\\\.|[^\\]\\\\])*]"), "").replace(Regex("""\{\d+(,\d+)?}"""), "")
        for (m in refs.findAll(body)) assertTrue(m.value in defined, m.value)
    }

    @Test
    fun `valid outputs match and parse`() {
        val good = listOf(
            """{"tool":"spend","args":{"query":"food"}}""",
            """{"tool": "spend", "args": {"query": "food", "period": "month", "compare": true}}""",
            """{"tool":"spend","args":{"query":"a \"b\" \\ \n \/ c","compare":false}}""",
            """{"tool":"spend","args":{"query":"₹500 chai दूध"}}""",
            """{"tool":"log_meal","args":{"meal":"dinner","items":[]}}""",
            """{"tool":"log_meal","args":{"meal":"lunch","items":[{"name":"dal","qty":1.5,"unit":"bowl"}]}}""",
            """{"tool": "log_meal", "args": {"meal": "snacks", "items": [{"name": "a", "qty": 2, "unit": "u", "brand": "b", "mods": ["x", "y"]}, {"name": "c", "qty": -0.25, "unit": "u", "mods": []}]}}""",
            """{"tool":"log_weight","args":{"kg":71.5}}""",
            """{"tool":"log_weight","args":{"kg":71,"when":"today"}}""",
            """{"tool":"end_trip","args":{}}""",
            """{"tool":"opts","args":{}}""",
            """{"tool":"opts","args":{"a":1}}""",
            """{"tool":"opts","args":{"b":-20}}""",
            """{"tool":"opts","args":{"c":"z"}}""",
            """{"tool":"opts","args":{"a":0,"b":3,"c":"z"}}""",
            """{"tool":"opts","args":{"a":0,"c":"z"}}""",
            """{"tool":"count","args":{"n":999999999999}}""",
            """{"say":"Hi"}""",
            """{"say": "Spent ₹500 on \"chai\".\nKaafi hai"}""",
            """{"say":""}""",
        )
        for (s in good) {
            ok(s)
            if (s != """{"say":""}""") assertFalse(chatDemo().parse(s) is Step.Bad, s)
        }
    }

    @Test
    fun `invalid outputs do not match`() {
        val bad = listOf(
            "",
            "hello",
            """{"tool":"nope","args":{}}""",
            """{"tool":"count","args":{}}""",
            """{"tool":"count","args":{"n":1.5}}""",
            """{"tool":"count","args":{"n":01}}""",
            """{"tool":"count","args":{"n":1000000000000}}""",
            """{"tool":"count","args":{"n":"1"}}""",
            """{"tool":"spend","args":{"period":"week","query":"x"}}""",
            """{"tool":"spend","args":{"query":"x","period":"year"}}""",
            """{"tool":"spend","args":{"query":"x","compare":"true"}}""",
            """{"tool":"spend","args":{"query":"x","extra":1}}""",
            """{"tool":"spend","args":{"query":"x"}""",
            """{"tool":"spend","args":{"query":"un"escaped"}}""",
            "{\"tool\":\"spend\",\"args\":{\"query\":\"line\nbreak\"}}",
            """{"tool":"spend","args":{"query":"bad \q escape"}}""",
            """{"tool":"spend","args":{"query":"${"a".repeat(301)}"}}""",
            """{"tool":"log_meal","args":{"meal":"lunch","items":[{"name":"a","qty":1}]}}""",
            """{"tool":"log_meal","args":{"meal":"lunch","items":[{"name":"a","qty":1,"unit":"u","mods":[1]}]}}""",
            """{"tool":"log_meal","args":{"meal":"lunch","items":[${List(17) { """{"name":"a","qty":1,"unit":"u"}""" }.joinToString(",")}]}}""",
            """{"tool":"opts","args":{"b":1,"a":2}}""",
            """{"tool":"opts","args":{"a":1,}}""",
            """{"tool":"end_trip"}""",
            """{"tool":"log_weight","args":{"when":"today","kg":71}}""",
            """{ "say":"x"}""",
            """{"say":"x"} """,
            """{"say":"${"a".repeat(1201)}"}""",
            """{"say":1}""",
        )
        for (s in bad) no(s)
    }

    @Test
    fun `the longest allowed strings match`() {
        ok("""{"tool":"spend","args":{"query":"${"a".repeat(300)}"}}""")
        ok("""{"say":"${"a".repeat(1200)}"}""")
    }

    @Test
    fun `a registry without tools only says`() {
        val e = Gbnf(Registry(emptyList()).grammar())
        assertTrue(e.matches("""{"say":"x"}"""))
        assertFalse(e.matches("""{"tool":"x","args":{}}"""))
    }
}
