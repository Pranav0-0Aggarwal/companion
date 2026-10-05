package app.companion.core

class Convo(val cap: Int = 12) {
    private val list = ArrayDeque<Turn>()

    val turns: List<Turn> get() = list.toList()

    fun add(t: Turn) {
        list.addLast(t)
        while (list.size > cap) list.removeFirst()
        while (list.isNotEmpty() && list.first().role != Role.User) list.removeFirst()
    }

    fun add(role: Role, text: String) = add(Turn(role, text))

    fun fit(extra: List<Turn>, ok: (List<Turn>) -> Boolean): List<Turn> {
        var t = turns
        while (t.isNotEmpty() && !ok(t + extra)) t = t.drop(1).dropWhile { it.role != Role.User }
        return t + extra
    }

    fun clear() = list.clear()
}

class SayStream {
    private var head = 0
    private var esc = false
    private var hex: StringBuilder? = null
    private var held: Char? = null
    private var dead = false
    private var end = false

    fun done() = end

    fun feed(chunk: String): String {
        val out = StringBuilder()
        held?.let { out.append(it) }
        held = null
        for (c in chunk) {
            if (end || dead) break
            when {
                head < OPEN.length -> when {
                    c.isWhitespace() -> {}
                    c == OPEN[head] -> head++
                    else -> dead = true
                }
                hex != null -> hex!!.append(c).takeIf { it.length == 4 }?.let {
                    out.append(it.toString().toIntOrNull(16)?.toChar() ?: '�')
                    hex = null
                }
                esc -> {
                    esc = false
                    when (c) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'r' -> out.append('\r')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000c')
                        'u' -> hex = StringBuilder()
                        else -> out.append(c)
                    }
                }
                c == '\\' -> esc = true
                c == '"' -> end = true
                else -> out.append(c)
            }
        }
        if (out.isNotEmpty() && out.last().isHighSurrogate()) {
            held = out.last()
            out.setLength(out.length - 1)
        }
        return out.toString()
    }

    private companion object {
        const val OPEN = "{\"say\":\""
    }
}
