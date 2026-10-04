package app.companion.core

internal object Txn {
    private val dr = Regex("(?i)\\b(debited|spent|paid|sent|withdrawn|purchase|txn of|used at)\\b")
    private val cr = Regex("(?i)\\b(credited|received|refund|deposited|cashback|salary)\\b")
    private val creditCard = Regex("(?i)credit\\s*card|\\bcc\\b|spent|used at")
    private val debitCard = Regex("(?i)debit\\s*card")
    private val upi = Regex("(?i)upi|vpa|@[a-z]{2,}")
    private val net = Regex("(?i)neft|imps|rtgs|net\\s?banking")
    private val card = Regex("(?i)\\bcard\\b")
    private val mask = Regex("(?i)(?<![a-z])[x*•]{1,12}[\\s-]?(\\d{4})(?![0-9])")
    private val ending = Regex("(?i)ending(?:\\s+(?:with|in))?\\s*:?\\s*[x*•]*\\s*(\\d{4})(?![0-9])")
    private val acct = Regex("(?i)(?:a/c|acct|account|card)(?:\\s+no\\.?)?\\s*:?\\s*(\\d{4})(?![0-9])")
    private const val END = "(?=\\s+(?:on|using|via|for|from|with|ref|refno|txn|upi|avl|bal|if|not|call|has|is)\\b|[.,;(]|\\s*$)"
    private val at = Regex("(?i)\\b(?:at|towards)\\s+([^\\n]{2,40}?)$END")
    private val to = Regex("(?i)\\b(?:trf to|paid to|to)\\s+(?:vpa\\s+)?([^\\n]{2,40}?)$END")
    private val from = Regex("(?i)\\b(?:from|by)\\s+(?:vpa\\s+)?([^\\n]{2,40}?)$END")
    private val bad = Regex("(?i)^(your|a/c|acct|account|card|the|my|you|this|credit|debit|bank|neft|imps|rtgs|upi|ach|cheque|cash|transfer)\\b")

    fun last4(t: String): String? =
        (ending.find(t) ?: mask.find(t) ?: acct.find(t))?.groupValues?.get(1)

    private fun name(raw: String?): String? {
        var s = raw?.trim()?.trimEnd('-', ' ', ':') ?: return null
        if (s.contains('@')) s = s.substringBefore('@')
        if (s.isBlank() || s.all { it.isDigit() || it == ' ' } || bad.containsMatchIn(s)) return null
        s = s.take(28).trim()
        return if (s.any { it.isLowerCase() } && s.any { it.isUpperCase() }) s else s.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar(Char::uppercase) }
    }

    private fun pick(re: Regex, t: String) = re.findAll(t).firstNotNullOfOrNull { name(it.groupValues[1]) }

    private fun mode(t: String) = when {
        upi.containsMatchIn(t) -> Mode.Upi
        net.containsMatchIn(t) -> Mode.Netbanking
        card.containsMatchIn(t) -> Mode.Card
        else -> Mode.Other
    }

    fun parse(r: Raw, t: String): Event.Move? {
        val d = dr.find(t)
        val c = cr.find(t)
        val credit = c != null && (d == null || c.range.first < d.range.first)
        if (d == null && c == null) return null
        val amt = Money.txn(t) ?: return null
        val last4 = last4(t)
        val bank = Brands.bank(r.sender, t)
        val mode = mode(t)
        return if (credit) {
            Event.Credit(amt.paise, amt.currency, last4, bank, pick(from, t) ?: Brands.shop("", t), mode)
        } else {
            val merchant = pick(at, t) ?: pick(to, t)
            if (mode == Mode.Card && creditCard.containsMatchIn(t) && !debitCard.containsMatchIn(t)) {
                Event.CardSpend(amt.paise, amt.currency, last4, bank, merchant)
            } else {
                Event.Debit(amt.paise, amt.currency, last4, bank, merchant, mode)
            }
        }
    }
}
