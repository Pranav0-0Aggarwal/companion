package app.companion.core

enum class Role { User, Assistant, Tool }

data class Turn(val role: Role, val text: String)

enum class ChatTemplate {
    Qwen, Lfm;

    val stop = listOf("<|im_end|>")

    fun render(system: String, turns: List<Turn>) = buildString {
        block("system", system)
        for (t in turns) when {
            t.role == Role.User -> block("user", t.text)
            t.role == Role.Assistant -> block("assistant", t.text)
            this@ChatTemplate == Qwen -> block("user", "<tool_response>\n${scrub(t.text)}\n</tool_response>", false)
            else -> block("tool", t.text)
        }
        append(if (this@ChatTemplate == Qwen) "<|im_start|>assistant\n<think>\n\n</think>\n\n" else "<|im_start|>assistant\n")
    }

    private fun StringBuilder.block(role: String, text: String, clean: Boolean = true) {
        append("<|im_start|>").append(role).append('\n').append(if (clean) scrub(text) else text).append("<|im_end|>\n")
    }
}

internal fun scrub(s: String) = s.replace("<|", "< |").replace("|>", "| >")

object Prompt {
    fun system(tools: String) = """You are Companion, a private on-device assistant for the owner's money, food, trips and documents.
Reply briefly, in the language the user writes in, Hinglish included.
Use tools for every fact and number; never guess amounts, dates or nutrition values, and never invent offers, discounts or advice the owner's data doesn't support. If a request is unclear, ask one short question.
Questions are never logs. Meals are breakfast, lunch, snacks or dinner; tea, coffee or a bite in the afternoon or evening is snacks. In Hinglish, kal about the past is yesterday, subah is breakfast and raat is dinner.
Examples:
lunch was 2 rotis and dal -> {"tool":"log_meal","args":{"meal":"lunch","when":"today","items":[{"name":"roti","qty":2},{"name":"dal","qty":1}]}}
kal raat 2 plate momos khaye -> {"tool":"log_meal","args":{"meal":"dinner","when":"yesterday","items":[{"name":"momos","qty":2,"unit":"plate"}]}}
how much protein did I eat today -> {"tool":"food_today","args":{"date":"today"}}
dining out last month vs this month -> {"tool":"spend","args":{"category":"food","period":"this month","compare":"last month"}}
Output exactly one JSON object per message and nothing else:
{"tool":"<name>","args":{...}} calls a tool. Its result arrives in the next message, then call another tool or answer.
{"say":"<reply>"} gives the final answer or asks the question.
Argument keys ending in ? are optional. Each user message starts with [now: ...], the current local date and time.
Tools, one JSON per line:
$tools"""

    fun user(text: String, now: String) = "[now: $now] $text"
}
