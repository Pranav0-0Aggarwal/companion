package app.companion.core

class Draft(val raw: String, val step: Step)

class ObjEnd {
    private var depth = 0
    private var str = false
    private var esc = false
    private var end = false

    fun done() = end

    fun feed(chunk: String): Boolean {
        for (c in chunk) {
            if (end) break
            when {
                esc -> esc = false
                str -> if (c == '\\') esc = true else if (c == '"') str = false
                c == '"' -> str = true
                c == '{' -> depth++
                c == '}' -> if (depth > 0 && --depth == 0) end = true
            }
        }
        return end
    }
}

object Decode {
    const val LEAD = "{\""
    const val FREE = 256

    suspend fun run(reg: Registry, gen: suspend (strict: Boolean) -> String?): Draft? {
        val free = gen(false)?.trim() ?: return null
        reg.parse(free).takeUnless { it is Step.Bad }?.let { return Draft(free, it) }
        val strict = gen(true)?.trim() ?: return null
        return Draft(strict, reg.parse(strict))
    }
}
