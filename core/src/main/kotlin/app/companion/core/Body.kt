package app.companion.core

object Body {
    const val MAX = 8000
    private const val DAY = 86_400_000L

    fun since(days: Int, now: Long) = if (days <= 0) Long.MIN_VALUE else now - days * DAY

    fun keep(e: Event, tags: List<String>, text: String, at: Long, since: Long): String? =
        if (e is Event.Otp || "otp" in tags || at < since) null else Redact.codes(text.trim()).take(MAX).ifBlank { null }
}
