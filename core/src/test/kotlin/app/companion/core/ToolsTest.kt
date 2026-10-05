package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal class DemoTool(override val name: String, override val args: List<Arg> = emptyList(), override val desc: String = "d") : Tool {
    override suspend fun run(a: Map<String, Any?>): ToolOut = ToolOut.Ok("")
}

internal fun chatDemo() = Registry(
    listOf(
        DemoTool("spend", listOf(Arg("query", Ty.Str, desc = "what"), Arg("period", Ty.Pick(listOf("today", "week", "month")), false), Arg("compare", Ty.Bool, false))),
        DemoTool(
            "log_meal",
            listOf(
                Arg("meal", Ty.Pick(listOf("breakfast", "lunch", "snacks", "dinner"))),
                Arg(
                    "items",
                    Ty.Arr(Ty.Obj(listOf(Arg("name", Ty.Str), Arg("qty", Ty.Num), Arg("unit", Ty.Str), Arg("brand", Ty.Str, false), Arg("mods", Ty.Arr(Ty.Str), false)))),
                ),
            ),
        ),
        DemoTool("log_weight", listOf(Arg("when", Ty.Str, false), Arg("kg", Ty.Num))),
        DemoTool("end_trip"),
        DemoTool("opts", listOf(Arg("a", Ty.Int, false), Arg("b", Ty.Int, false), Arg("c", Ty.Str, false))),
        DemoTool("count", listOf(Arg("n", Ty.Int))),
    ),
)

class ToolsTest {
    private val r = chatDemo()

    private fun bad(s: String) = assertIs<Step.Bad>(r.parse(s), s)

    private fun call(s: String) = assertIs<Step.Call>(r.parse(s), s)

    @Test
    fun `a valid call is parsed with typed arguments`() {
        val c = call("""{"tool":"log_meal","args":{"meal":"lunch","items":[{"name":"dal","qty":2,"unit":"bowl","mods":["extra ghee"]}]}}""")
        assertEquals("log_meal", c.tool.name)
        assertEquals(mapOf("meal" to "lunch", "items" to listOf(mapOf("name" to "dal", "qty" to 2.0, "unit" to "bowl", "mods" to listOf("extra ghee")))), c.args)
    }

    @Test
    fun `ints stay longs and nums become doubles`() {
        assertEquals(mapOf("n" to 7L), call("""{"tool":"count","args":{"n":7}}""").args)
        assertEquals(mapOf("kg" to 71.5), call("""{"tool":"log_weight","args":{"kg":71.5}}""").args)
    }

    @Test
    fun `unknown keys are dropped and nulls on optionals skipped`() {
        assertEquals(mapOf("query" to "tea"), call("""{"tool":"spend","args":{"query":"tea","extra":1,"period":null}}""").args)
        assertEquals(emptyMap(), call("""{"tool":"end_trip"}""").args)
        assertEquals(emptyMap(), call("""{"tool":"end_trip","args":{"x":1}}""").args)
        assertEquals("end_trip", call("""{"tool":"end_trip","args":{},"zzz":true}""").tool.name)
    }

    @Test
    fun `surrounding whitespace and key order are free`() {
        assertEquals(mapOf("kg" to 70.0, "when" to "today"), call(" {\"args\": {\"kg\": 70, \"when\": \"today\"}, \"tool\": \"log_weight\"}\n").args)
    }

    @Test
    fun `say is returned as the final text`() {
        assertEquals(Step.Say("Done \"ok\"\n"), r.parse("""{"say":"Done \"ok\"\n"}"""))
        assertEquals(Step.Say("₹500 kharch hua"), r.parse("""{"say":"₹500 kharch hua"}"""))
    }

    @Test
    fun `invalid json and shapes are bad`() {
        for (s in listOf("", "hello", "[1]", "{", """{"say":"x"} trailing""", """{"say":""}""", """{"say":"  "}""", """{"say":5}""", "{}", """{"tool":5}""", """{"tool":"nope","args":{}}""")) bad(s)
    }

    @Test
    fun `required and typed arguments are enforced`() {
        for (
            s in listOf(
                """{"tool":"count","args":{}}""",
                """{"tool":"count"}""",
                """{"tool":"count","args":{"n":null}}""",
                """{"tool":"count","args":{"n":"7"}}""",
                """{"tool":"count","args":{"n":1.5}}""",
                """{"tool":"count","args":{"n":1e3}}""",
                """{"tool":"count","args":{"n":1000000000000}}""",
                """{"tool":"count","args":{"n":-1000000000000}}""",
                """{"tool":"count","args":[1]}""",
                """{"tool":"count","args":"n"}""",
                """{"tool":"log_weight","args":{"kg":"70"}}""",
                """{"tool":"log_weight","args":{"kg":1e20}}""",
                """{"tool":"spend","args":{"query":"x","compare":"yes"}}""",
                """{"tool":"spend","args":{"query":"x","period":"year"}}""",
                """{"tool":"spend","args":{"query":5}}""",
                """{"tool":"log_meal","args":{"meal":"brunch","items":[]}}""",
                """{"tool":"log_meal","args":{"meal":"lunch","items":{}}}""",
                """{"tool":"log_meal","args":{"meal":"lunch","items":[{"name":"a","qty":1}]}}""",
                """{"tool":"log_meal","args":{"meal":"lunch","items":[{"name":"a","qty":1,"unit":"x","mods":[1]}]}}""",
                """{"tool":"log_meal","args":{"meal":"lunch","items":["a"]}}""",
            )
        ) bad(s)
    }

    @Test
    fun `length caps hold`() {
        val ok = "a".repeat(300)
        assertTrue(r.parse("""{"tool":"spend","args":{"query":"$ok"}}""") is Step.Call)
        bad("""{"tool":"spend","args":{"query":"${ok}a"}}""")
        val item = """{"name":"a","qty":1,"unit":"u"}"""
        fun meal(n: Int) = """{"tool":"log_meal","args":{"meal":"lunch","items":[${List(n) { item }.joinToString(",")}]}}"""
        assertTrue(r.parse(meal(16)) is Step.Call)
        bad(meal(17))
        assertTrue(r.parse("""{"say":"${"b".repeat(1200)}"}""") is Step.Say)
        bad("""{"say":"${"b".repeat(1201)}"}""")
    }

    @Test
    fun `the reason never echoes model or user text`() {
        val w = assertIs<Step.Bad>(r.parse("""{"tool":"secret-name-xyz","args":{}}""")).why
        val v = assertIs<Step.Bad>(r.parse("""{"tool":"spend","args":{"query":"x","period":"secret-val-xyz"}}""")).why
        assertFalse("secret" in w || "secret" in v)
        assertEquals("period", v)
    }

    @Test
    fun `listing is one compact json line per tool`() {
        val t = Registry(
            listOf(
                DemoTool("spend", listOf(Arg("note", Ty.Str, false), Arg("query", Ty.Str, desc = "what"), Arg("p", Ty.Pick(listOf("a", "b"))), Arg("xs", Ty.Arr(Ty.Obj(listOf(Arg("k", Ty.Num))))), Arg("ok", Ty.Bool), Arg("n", Ty.Int)), "Spend."),
                DemoTool("end_trip", desc = "End."),
            ),
        )
        assertEquals(
            """{"name":"spend","desc":"Spend.","args":{"query":"str, what","p":"a|b","xs":[{"k":"num"}],"ok":"bool","n":"int","note?":"str"}}""" + "\n" +
                """{"name":"end_trip","desc":"End.","args":{}}""",
            t.listing(),
        )
    }
}
