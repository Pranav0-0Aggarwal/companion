package app.companion.core

object Prompts {
    fun ask(name: String, today: String): String =
        "You are Companion, a private assistant on the owner's phone${if (name.isBlank()) "" else ", talking to $name"}. Today is $today. " +
            "Answer briefly in plain language. Use the tools to look things up; never guess numbers. " +
            "Writing tools only propose: the owner confirms with one tap. ${Untrusted.RULE}"

    fun brief(name: String, facts: List<String>): String =
        "Write a calm two sentence morning brief for ${name.ifBlank { "the owner" }}, starting with \"Hi${if (name.isBlank()) "" else " $name"},\". " +
            "Use only these facts, plain words, no emoji.\n" + facts.joinToString("\n") { "- $it" }

    fun digest(source: String, msgs: List<Pair<String, String>>): String =
        "Summarise these $source messages in at most three short lines: who needs a reply, any money, any plan with a date. ${Untrusted.RULE}\n" +
            msgs.joinToString("\n") { Untrusted.wrap(it.first, it.second) }

    fun reply(sender: String, thread: List<String>): String =
        "Draft one short, friendly reply the owner could send to the last message. It will be copied by hand and never sent automatically. ${Untrusted.RULE}\n" +
            Untrusted.wrap(sender, thread.joinToString("\n"), 1200)
}
