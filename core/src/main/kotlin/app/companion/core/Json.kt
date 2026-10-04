package app.companion.core

object Json {
    fun parse(text: String): Any? = Reader(text).run { ws(); value().also { ws(); check(i == s.length) { "trailing" } } }

    fun write(v: Any?): String = when (v) {
        null -> "null"
        is String -> quote(v)
        is Float -> if (v.isFinite()) v.toString() else "null"
        is Double -> if (v.isFinite()) v.toString() else "null"
        is Number, is Boolean -> v.toString()
        is Map<*, *> -> v.entries.joinToString(",", "{", "}") { quote(it.key as String) + ":" + write(it.value) }
        is List<*> -> v.joinToString(",", "[", "]") { write(it) }
        else -> error("type")
    }

    private fun quote(s: String) = buildString {
        append('"')
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
        append('"')
    }

    @Suppress("UNCHECKED_CAST")
    fun obj(text: String): Map<String, Any?> = parse(text) as Map<String, Any?>

    private class Reader(val s: String) {
        var i = 0

        fun ws() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            ws()
            return when (val c = s[i]) {
                '{' -> map()
                '[' -> list()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else error("unexpected")
            }
        }

        fun lit(w: String, v: Any?): Any? {
            check(s.startsWith(w, i)) { "literal" }
            i += w.length
            return v
        }

        fun num(): Any {
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            val t = s.substring(st, i)
            return t.toLongOrNull() ?: t.toDouble()
        }

        fun str(): String {
            val sb = StringBuilder()
            i++
            while (s[i] != '"') {
                if (s[i] == '\\') {
                    i++
                    when (val e = s[i]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000c')
                        'u' -> {
                            sb.append(s.substring(i + 1, i + 5).toInt(16).toChar())
                            i += 4
                        }
                        else -> sb.append(e)
                    }
                } else {
                    sb.append(s[i])
                }
                i++
            }
            i++
            return sb.toString()
        }

        fun list(): List<Any?> {
            val out = mutableListOf<Any?>()
            i++
            ws()
            if (s[i] == ']') return out.also { i++ }
            while (true) {
                out.add(value())
                ws()
                if (s[i++] == ']') return out
            }
        }

        fun map(): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            i++
            ws()
            if (s[i] == '}') return out.also { i++ }
            while (true) {
                ws()
                val k = str()
                ws()
                check(s[i++] == ':') { "colon" }
                out[k] = value()
                ws()
                if (s[i++] == '}') return out
            }
        }
    }
}
