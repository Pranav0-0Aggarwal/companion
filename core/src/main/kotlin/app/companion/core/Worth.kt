package app.companion.core

object Worth {
    private val ask = Regex("(?i)\\?|^(can|could|will|would|are|is|do|did|when|where|what|who|how|why|please|pls)\\b")
    private val money = Regex("(?i)₹|(?<![a-z])rs\\.?\\s*\\d|\\binr\\b|\\bupi\\b|\\bpaid\\b|\\bpay\\b|\\bsend\\b|\\bsent\\b|\\bowe|\\btransfer|\\bsplit\\b")
    private val plan = Regex(
        "(?i)\\b(today|tonight|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday|meet|lunch|dinner|call|plan|party|trip|reach|come|coming|deadline|urgent)\\b|\\b\\d{1,2}(:\\d{2})?\\s*(am|pm)\\b|\\b\\d{1,2}[/-]\\d{1,2}\\b",
    )

    fun dm(sender: String, text: String, important: Set<String>): Boolean {
        val s = sender.trim().lowercase()
        return important.any { it.isNotBlank() && it.trim().lowercase() == s } ||
            ask.containsMatchIn(text.trim()) || money.containsMatchIn(text) || plan.containsMatchIn(text)
    }
}
