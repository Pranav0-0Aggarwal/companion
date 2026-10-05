package app.companion.ui

object Voice {
    fun addr(name: String, text: String) = if (name.isBlank()) text.replaceFirstChar(Char::uppercase) else "$name, $text"

    fun greet(name: String, n: Int) = when {
        n == 0 -> if (name.isBlank()) "All clear" else "All clear, $name"
        else -> "${if (name.isBlank()) "Hi" else "Hi $name"}, $n thing${if (n == 1) "" else "s"} need${if (n == 1) "s" else ""} your attention"
    }

    fun hello(name: String, hour: Int): String {
        val part = when (hour) {
            in 5..11 -> "Morning"
            in 12..16 -> "Afternoon"
            else -> "Evening"
        }
        return if (name.isBlank()) part else "$part, $name"
    }

    fun need(n: Int) = if (n == 0) "nothing needs you" else "$n thing${if (n == 1) "" else "s"} need${if (n == 1) "s" else ""} you"

    fun due(name: String, title: String, days: Int) = addr(name, "$title due ${inDays(days)}")

    fun otp(name: String, service: String) = addr(name, "$service code")

    fun rel(n: Int) = when {
        n == 0 -> "Today"
        n == -1 -> "Yesterday"
        n == 1 -> "Tomorrow"
        n < 0 -> "${-n} days ago"
        else -> "In $n days"
    }
}
