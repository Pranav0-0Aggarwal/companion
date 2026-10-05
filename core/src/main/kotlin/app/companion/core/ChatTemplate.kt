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
Use tools for every fact and number; never guess amounts, dates or nutrition values. If a request is unclear, ask one short question.
Output exactly one JSON object per message and nothing else:
{"tool":"<name>","args":{...}} calls a tool. Its result arrives in the next message, then call another tool or answer.
{"say":"<reply>"} gives the final answer or asks the question.
Argument keys ending in ? are optional. Each user message starts with [now: ...], the current local date and time.
Tools, one JSON per line:
$tools"""

    fun user(text: String, now: String) = "[now: $now] $text"
}
