package app.companion.ui

object Voice {
    fun addr(name: String, text: String) = if (name.isBlank()) text.replaceFirstChar(Char::uppercase) else "$name, $text"

    fun greet(name: String, n: Int) = when {
        n == 0 -> if (name.isBlank()) "All clear" else "All clear, $name"
        else -> "${if (name.isBlank()) "Hi" else "Hi $name"}, $n thing${if (n == 1) "" else "s"} need${if (n == 1) "s" else ""} your attention"
    }

    fun due(name: String, title: String, days: Int) = addr(name, "$title due ${inDays(days)}")

    fun otp(name: String, service: String) = addr(name, "$service code")
}
