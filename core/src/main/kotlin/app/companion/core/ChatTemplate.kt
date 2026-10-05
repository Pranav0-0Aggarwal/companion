package app.companion.core

enum class Role { User, Assistant, Tool }

data class Turn(val role: Role, val text: String)

enum class ChatTemplate {
    Qwen, Lfm;

    val stop = listOf("<|im_end|>")

    fun head(system: String) = buildString { block("system", system) }

    fun render(system: String, turns: List<Turn>, lead: String = "") = buildString {
        append(head(system))
        for (t in turns) when {
            t.role == Role.User -> block("user", t.text)
            t.role == Role.Assistant -> block("assistant", t.text)
            this@ChatTemplate == Qwen -> block("user", "<tool_response>\n${scrub(t.text)}\n</tool_response>", false)
            else -> block("tool", t.text)
        }
        append(if (this@ChatTemplate == Qwen) "<|im_start|>assistant\n<think>\n\n</think>\n\n" else "<|im_start|>assistant\n").append(lead)
    }

    private fun StringBuilder.block(role: String, text: String, clean: Boolean = true) {
        append("<|im_start|>").append(role).append('\n').append(if (clean) scrub(text) else text).append("<|im_end|>\n")
    }
}

internal fun scrub(s: String) = s.replace("<|", "< |").replace("|>", "| >")

object Prompt {
    fun system(tools: String) = """You are Companion, an on-device assistant for the owner's money, food, trips and documents.
Reply briefly in the user's language, Hinglish too.
Use tools for every fact and number; never guess amounts, dates or nutrition, never invent offers or records. Meal ideas are fine. After a tool result, reply with say unless you need another tool. If unclear, ask one question.
Questions are never logs. Meals are breakfast, lunch, snacks or dinner; tea or coffee in the afternoon or evening is snacks. In Hinglish, kal about the past is yesterday, subah breakfast, raat dinner.
Examples:
kal raat 2 plate momos aur ek roll khaya -> {"tool":"log_meal","args":{"meal":"dinner","when":"yesterday","items":[{"name":"momos","qty":2,"unit":"plate"},{"name":"roll","qty":1}]}}
how much protein did I eat today -> {"tool":"food_today","args":{"date":"today"}}
dining out last month vs this month -> {"tool":"spend","args":{"category":"food","period":"this month","compare":"last month"}}
Reply with one JSON object only: {"tool":"<name>","args":{...}} calls a tool, its result comes next; {"say":"<reply>"} answers or asks.
Each user message starts with [now: local date and time]. ? marks an optional argument.
Tools, name(args): purpose
$tools"""

    const val ANSWER = "You already have this result above. Answer now with {\"say\":...}."

    fun user(text: String, now: String) = "[now: $now] $text"
}
