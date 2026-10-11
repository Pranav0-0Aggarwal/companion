package app.companion.core

object CallInfo {
    private val at = Regex("""\+?\d[\d ()-]{5,}\d""")

    private val dial = Regex("""\+?[0-9 ()-]{7,32}""")

    fun mine(noteId: String?, noteAt: Long, id: String, since: Long) = noteId == id || (noteId == null && noteAt >= since)

    fun dialable(s: String) = dial.matches(s)

    fun number(text: String): String? = at.find(text)?.value?.filter { it.isDigit() || it == '+' }
}
