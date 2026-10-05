package app.companion.core

internal object Grammar {
    private const val STR = """"\"" ([^"\\\x00-\x1f] | "\\" ["\\/bnrt]){0,"""

    fun of(tools: List<Tool>): String {
        val g = Gen()
        val calls = tools.map { t ->
            val r = "c-" + dash(t.name)
            g.rules[r] = lit("{\"tool\":") + " ws " + lit(Json.write(t.name) + ",") + " ws " + lit("\"args\":") + " ws " + g.obj(t.args, r) + " " + lit("}")
            r
        }
        val head = if (calls.isEmpty()) "root ::= say" else "root ::= call | say\ncall ::= " + calls.joinToString(" | ")
        return buildString {
            appendLine(head)
            appendLine("say ::= " + lit("{\"say\":") + " ws say-str " + lit("}"))
            g.rules.forEach { (k, v) -> appendLine("$k ::= $v") }
            appendLine("say-str ::= $STR${Cap.SAY}} \"\\\"\"")
            appendLine("str ::= $STR${Cap.STR}} \"\\\"\"")
            appendLine("""int ::= "-"? ("0" | [1-9] [0-9]{0,${Cap.DIGITS - 1}})""")
            appendLine("""num ::= int ("." [0-9]{1,6})?""")
            appendLine("""bool ::= "true" | "false"""")
            append("ws ::= [ ]?")
        }
    }

    private fun dash(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun lit(s: String) = '"' + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + '"'

    private class Gen {
        val rules = LinkedHashMap<String, String>()

        fun ty(t: Ty, at: String): String = when (t) {
            Ty.Str -> "str"
            Ty.Int -> "int"
            Ty.Num -> "num"
            Ty.Bool -> "bool"
            is Ty.Pick -> t.v.joinToString(" | ", "(", ")") { lit(Json.write(it)) }
            is Ty.Arr -> {
                val e = ty(t.of, "$at-item")
                rules[at] = """"[" ($e ("," ws $e){0,${Cap.ARR - 1}})? "]""""
                at
            }
            is Ty.Obj -> {
                rules[at] = obj(t.f, at)
                at
            }
        }

        fun obj(f: List<Arg>, at: String): String {
            val o = ordered(f)
            val kv = o.map { lit(Json.write(it.name) + ":") + " ws " + ty(it.ty, at + "-" + dash(it.name)) }
            val n = o.count { it.req }
            val opt = kv.drop(n).map { """("," ws $it)?""" }
            return when {
                kv.isEmpty() -> lit("{}")
                n > 0 -> (listOf(lit("{")) + kv.take(n).joinToString(""" "," ws """) + opt + lit("}")).joinToString(" ")
                else -> lit("{") + " (" + kv.indices.joinToString(" | ") { i -> kv[i] + opt.drop(i + 1).joinToString("") { " $it" } } + ")? " + lit("}")
            }
        }
    }
}
