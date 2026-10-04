package app.companion.core

internal object Otp {
    private const val C = "(?<![0-9-])(\\d{4,8})(?![0-9-])"
    private val cue = Regex("(?i)\\b(otp|one[- ]time|verification code|security code|passcode|login code|auth\\w* code|code|pin|password)\\b")
    private val moved = Regex("(?i)debited|credited|spent|withdrawn|\\bpaid\\b|received")
    private val lead = Regex("(?i)$C\\s+(?:is|as)\\s+(?:your|the|an?|ur)\\b")
    private val near = Regex("(?i)\\b(?:otp|code|password|passcode|pin)\\b[^0-9]{0,40}?$C")
    private val verb = Regex("(?i)\\b(?:use|enter|share|submit)\\s+(?:the\\s+)?(?:code\\s+|otp\\s+)?$C")
    private val google = Regex("\\bG-(\\d{6})")
    private val scrub = listOf(
        Regex("(?i)(?<![a-z])(?:rs\\.?|inr|₹|usd|\\$)\\s*[0-9][0-9,]*(?:\\.[0-9]{1,2})?"),
        Regex("(?i)[0-9][0-9,]*(?:\\.[0-9]{1,2})?\\s*(?:inr|rs)(?![a-z])"),
        Regex("(?i)(?:[x*•]+\\s?|ending(?:\\s+(?:with|in))?\\s*)\\d{4}"),
        Regex("\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}"),
        Regex("\\d{1,2}:\\d{2}"),
    )
    private val ttl = Regex("(?i)(?:valid|expir\\w*|within|active|next)[^.\\d]{0,25}?(\\d+)\\s*(sec|min|hour|hr)")

    private val purposes = listOf(
        Regex("(?i)log\\s?in|sign\\s?in") to "login",
        Regex("(?i)deliver") to "delivery",
        Regex("(?i)transaction|txn|payment|purchase|\\bcard\\b") to "payment",
        Regex("(?i)regist|verif|sign\\s?up") to "verify",
    )

    fun parse(r: Raw, text: String): Event.Otp? {
        if (!cue.containsMatchIn(text)) return null
        val t = scrub.fold(text) { s, re -> re.replace(s, " ") }
        val code = if (moved.containsMatchIn(t)) {
            (lead.find(t) ?: verb.find(t))?.groupValues?.get(1) ?: google.find(t)?.groupValues?.get(1)
        } else {
            (lead.find(t) ?: near.find(t) ?: verb.find(t))?.groupValues?.get(1) ?: google.find(t)?.groupValues?.get(1)
        } ?: return null
        return Event.Otp(code, r.at + seconds(text) * 1000, Brands.service(r.sender, text), purposes.firstOrNull { it.first.containsMatchIn(text) }?.second)
    }

    private fun seconds(t: String): Long {
        val m = ttl.find(t) ?: return 600
        val n = m.groupValues[1].toLongOrNull() ?: return 600
        return when (m.groupValues[2].lowercase()) {
            "sec" -> n
            "min" -> n * 60
            else -> n * 3600
        }.coerceIn(30, 3600)
    }
}
