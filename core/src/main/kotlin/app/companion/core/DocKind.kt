package app.companion.core

enum class DocKind(val label: String) {
    Insurance("Insurance"), Rc("Registration"), Puc("Pollution"), Fastag("FASTag"), Loan("Loan"), Warranty("Warranty"), Other("Other")
}

object Mask {
    private const val DOT = "••••"

    fun number(s: String): String {
        val c = s.filter { it.isLetterOrDigit() }
        val n = if (c.length >= 8) 4 else if (c.length >= 3) 2 else 0
        return DOT + c.takeLast(n)
    }

    fun title(kind: DocKind, number: String?): String = if (number.isNullOrBlank()) kind.label else "${kind.label} ${number(number)}"
}
