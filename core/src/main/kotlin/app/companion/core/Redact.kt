package app.companion.core

object Redact {
    private val cue = Regex("(?i)\\b(otp|one[- ]time|verification|security code|passcode|login code|auth\\w* code|code|pin|password)\\b")
    private val digits = Regex("(?<![0-9])(?<![.,][0-9])(?:G-)?\\d{4,8}(?![0-9%])(?![.,][0-9])")

    fun codes(text: String): String = if (cue.containsMatchIn(text)) digits.replace(text, "[redacted]") else text
}
