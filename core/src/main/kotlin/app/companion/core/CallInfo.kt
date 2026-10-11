package app.companion.core

object CallInfo {
    private val at = Regex("""\+?\d[\d ()-]{5,}\d""")

    fun number(text: String): String? = at.find(text)?.value?.filter { it.isDigit() || it == '+' }
}
