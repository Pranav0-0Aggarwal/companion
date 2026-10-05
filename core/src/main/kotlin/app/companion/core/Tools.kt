package app.companion.core

import kotlin.math.abs

sealed interface Ty {
    data object Str : Ty
    data object Int : Ty
    data object Num : Ty
    data object Bool : Ty
    data class Pick(val v: List<String>) : Ty
    data class Arr(val of: Ty) : Ty
    data class Obj(val f: List<Arg>) : Ty
}

data class Arg(val name: String, val ty: Ty, val req: Boolean = true, val desc: String = "")

sealed interface ToolOut {
    data class Ok(val text: String) : ToolOut
    data class Ask(val question: String) : ToolOut
    data class Fail(val why: String) : ToolOut
}

interface Tool {
    val name: String
    val desc: String
    val args: List<Arg>
    suspend fun run(a: Map<String, Any?>): ToolOut
}

sealed interface Step {
    data class Call(val tool: Tool, val args: Map<String, Any?>) : Step
    data class Say(val text: String) : Step
    data class Bad(val why: String) : Step
}

internal object Cap {
    const val STR = 300
    const val SAY = 1200
    const val ARR = 16
    const val DIGITS = 12
    const val MAG = 1_000_000_000_000L
}

internal fun ordered(f: List<Arg>) = f.sortedBy { !it.req }

class Registry(val tools: List<Tool>) {
    private val byName = tools.associateBy { it.name }

    fun grammar() = Grammar.of(tools)

    fun listing() = tools.joinToString("\n") { "${it.name}(${sig(it.args)}): ${it.desc}" }

    fun parse(out: String): Step {
        val m = runCatching { Json.obj(out.trim()) }.getOrNull() ?: return Step.Bad("json")
        val say = m["say"]
        if (say != null && m["tool"] == null) return if (say is String && say.isNotBlank() && size(say) <= Cap.SAY) Step.Say(say) else Step.Bad("say")
        val tool = (m["tool"] as? String)?.let(byName::get) ?: return Step.Bad("tool")
        return try {
            Step.Call(tool, obj(tool.args, m["args"] ?: emptyMap<String, Any?>(), "args"))
        } catch (e: Invalid) {
            Step.Bad(e.message!!)
        }
    }

    private class Invalid(at: String) : Exception(at)

    private fun size(s: String) = s.codePointCount(0, s.length)

    private fun obj(f: List<Arg>, v: Any?, at: String): Map<String, Any?> {
        val m = v as? Map<*, *> ?: throw Invalid(at)
        val out = LinkedHashMap<String, Any?>()
        for (a in f) {
            val x = m[a.name]
            if (x == null) {
                if (a.req) throw Invalid(a.name)
            } else {
                out[a.name] = conv(a.ty, x, a.name)
            }
        }
        return out
    }

    private fun conv(t: Ty, v: Any?, at: String): Any = when (t) {
        Ty.Str -> (v as? String)?.takeIf { size(it) <= Cap.STR }
        Ty.Int -> (v as? Long)?.takeIf { it > -Cap.MAG && it < Cap.MAG }
        Ty.Num -> (v as? Number)?.toDouble()?.takeIf { it.isFinite() && abs(it) < Cap.MAG }
        Ty.Bool -> v as? Boolean
        is Ty.Pick -> (v as? String)?.takeIf { it in t.v }
        is Ty.Arr -> (v as? List<*>)?.takeIf { it.size <= Cap.ARR }?.map { conv(t.of, it, at) }
        is Ty.Obj -> if (v is Map<*, *>) obj(t.f, v, at) else null
    } ?: throw Invalid(at)

    private fun doc(t: Ty): String = when (t) {
        Ty.Str -> "str"
        Ty.Int -> "int"
        Ty.Num -> "num"
        Ty.Bool -> "bool"
        is Ty.Pick -> t.v.joinToString("|")
        is Ty.Arr -> "[${doc(t.of)}]"
        is Ty.Obj -> "{${sig(t.f)}}"
    }

    private fun sig(f: List<Arg>) = ordered(f).joinToString(", ") { it.name + ":" + doc(it.ty) + if (it.req) "" else "?" }
}
