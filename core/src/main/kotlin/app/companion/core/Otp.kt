package app.companion.core

internal object Otp {
    private const val C = "(?<![\\p{L}\\d-])(\\d{4,8})(?![\\p{L}\\d])(?!-\\d)"
    private const val STRONG = "otp|o\\.t\\.p|one[- ]?time(?:\\s+(?:password|pin|code))?|(?:verification|security|login|auth\\w*|confirmation|verify)\\s+code|passcode|ओटीपी|वन टाइम पासवर्ड|सत्यापन कोड"
    private const val NOUN = "otp|ओटीपी|code|कोड|passcode|password|पासवर्ड|pin|one[- ]?time|verification|security|login|secret"
    private val cue = Regex("(?i)\\b(?:$STRONG|code|pin|password)\\b|ओटीपी|कोड|पासवर्ड")
    private val moved = Regex("(?i)debited|credited|spent|withdrawn")
    private const val DET = "your|the|an?|ur|aapka|apka|आपका|secret"
    private val lead = Regex("(?i)$C[\\s.:,-]{0,3}(?:(?:is|as|hai|है)\\s+(?:$DET|$NOUN)|(?:(?:$DET)\\s+)?(?:otp|ओटीपी)\\b)")
    private val strong = Regex("(?i)(?:$STRONG)[^\\n]{0,110}?(?:\\b(?:is|are|hai|है)\\b|[:=-])\\s*$C")
    private val weak = Regex("(?i)(?:\\bcode|\\bpin|\\bpassword|कोड)\\b[^0-9\\n]{0,25}?(?:\\b(?:is|are|hai|है)\\b|[:=-])\\s*$C")
    private val direct = Regex("(?i)(?:\\b(?:$STRONG|code)|ओटीपी|कोड)[\\s:=-]{1,3}$C")
    private val verb = Regex("(?i)\\b(?:use|enter|share|submit|give|quote|provide|type|input|bataye|batayein|batao)\\s+(?:the\\s+|this\\s+|your\\s+)?(?:(?:pickup\\s+|delivery\\s+)?code\\s+|otp\\s+|pin\\s+)?$C")
    private val deal = Regex("(?i)%\\s*off|\\boff\\b|\\bflat\\b|discount|coupon|voucher|promo|cashback|\\bsale\\b")
    private val google = Regex("\\bG-(\\d{6})")
    private val skip = Regex("(?i)(?:response|promo|coupon|voucher|referral|cancellation|discount|pin(?=\\s*code)|zip|area|ifsc|tracking|cvv|reference|gift|reward)\\s*(?:code|no\\.?|number)?\\s*[:=-]?\\s*(?:is\\s*)?$")
    private val scrub = listOf(
        Regex("(?i)(?<![a-z])(?:rs\\.?|inr|₹|usd|\\$)\\s*[0-9][0-9,]*(?:\\.[0-9]{1,2})?"),
        Regex("(?i)[0-9][0-9,]*(?:\\.[0-9]{1,2})?\\s*(?:inr|rs)(?![a-z])"),
        Regex("(?i)(?:[x*•]+\\s?|ending(?:\\s+(?:with|in))?\\s*)\\d{2,4}"),
        Regex("\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}"),
        Regex("\\d{1,2}:\\d{2}(?::\\d{2})?"),
        Regex("(?i)\\b(?:ref(?:erence)?|order|txn|trxn|awb|folio|booking|request|invoice|utr|rrn|id|no\\.?|number)\\s*(?:id|no\\.?|number)?\\s*[:#.-]?\\s*[a-z]{0,4}\\d[a-z0-9/_-]*"),
        Regex("(?:\\+?91[-\\s]?)?[6-9]\\d{9}"),
    )
    private val named = Regex("(?i)\\b(?:$STRONG)\\b|ओटीपी")
    private val token = Verbatim.code("\\d{4,8}")
    private val intent = Regex("(?i)\\b(?:verify|verification|log\\s?in|sign\\s?in|authenticat\\w*|confirm\\w*|(?:do not|don't|never)\\s+share)\\b")
    private val ttl = Regex("(?i)(?:valid|expir\\w*|within|active|next)[^.\\d]{0,25}?(\\d+)\\s*(sec|min|hour|hr)")

    private val purposes = listOf(
        Regex("(?i)log\\s?in|sign\\s?in") to "login",
        Regex("(?i)deliver|pick-?up|rider|\\bawb\\b|courier|shipment|doorstep") to "delivery",
        Regex("(?i)transaction|txn|trxn|payment|purchase|\\bcard\\b") to "payment",
        Regex("(?i)regist|verif|sign\\s?up") to "verify",
    )

    private fun clean(text: String) = scrub.fold(text) { s, re -> re.replace(s, " ") }

    private fun free(t: String, re: Regex) = re.findAll(t).any { m -> !skip.containsMatchIn(t.substring(maxOf(0, m.range.first - 30), m.range.first)) }

    private fun first(t: String, vararg res: Regex): String? = res.firstNotNullOfOrNull { re ->
        re.findAll(t).firstOrNull { m -> !skip.containsMatchIn(t.substring(maxOf(0, m.groups.last()!!.range.first - 30), m.groups.last()!!.range.first)) }?.groupValues?.last()
    }

    fun parse(r: Raw, text: String): Event.Otp? {
        if (!cue.containsMatchIn(text) || Misc.promoSender(r.sender)) return null
        val t = clean(text)
        val code = when {
            moved.containsMatchIn(t) -> first(t, lead, verb)
            deal.containsMatchIn(t) -> first(t, lead, strong)
            else -> first(t, lead, strong, weak, direct, verb)
        } ?: google.find(t)?.groupValues?.get(1) ?: return null
        return make(r, text, code)
    }

    fun make(r: Raw, text: String, code: String) =
        Event.Otp(code, r.at + seconds(text) * 1000, Brands.service(r.sender, text), purposes.firstOrNull { it.first.containsMatchIn(text) }?.second)

    fun maybe(r: Raw, text: String): Boolean {
        if (Misc.promoSender(r.sender)) return false
        val t = clean(text)
        if (!free(t, token)) return false
        return if (deal.containsMatchIn(t)) named.containsMatchIn(text) else cue.containsMatchIn(text) || intent.containsMatchIn(text)
    }

    fun fits(text: String, code: String) = free(clean(text), Verbatim.code(Regex.escape(code)))

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
