package app.companion.core

internal object Txn {
    private val dr = Regex("(?i)\\b(debited|spent|paid|sent|withdrawn|purchase|txn of|trxn of|used at|deducted|transferred(?! to your)|debit)\\b")
    private val weak = Regex("(?i)\\b(?:transaction|payment) of\\b")
    private val cr = Regex("(?i)\\b(credited|received|refund(?:ed)?|deposited|salary|reversed|added to|liquidated|matured|settlement)\\b")
    private val noDr = Regex("(?i)will be (?:debited|deducted|processed)|\\brequest(?:ed|s)?\\b|declined|failed|unsuccessful|not successful|unable|insufficient|could not|loan|pre-?approved")
    private val noCr = Regex("(?i)\\brequest(?:ed|s)?\\b|can be credited|loan|pre-?approved")
    private val creditCard = Regex("(?i)credit\\s*card|\\bcc\\b|spent|used at")
    private val debitCard = Regex("(?i)debit\\s*card")
    private val upi = Regex("(?i)\\bupi\\b|\\bvpa\\b|@(?:ok[a-z]+|ybl|ibl|axl|paytm|apl|upi|sbi|icici|hdfcbank|axisbank|axb|pthdfc|ptyes|yapl|slc|[a-z]{2,6}bank)(?!\\w|\\.[a-z])")
    private val net = Regex("(?i)neft|imps|rtgs|net\\s?banking")
    private val card = Regex("(?i)\\bcard\\b")
    private val mask = Regex("(?i)(?<![a-z])[x*•]{1,12}[\\s-]?\\d{0,8}?(\\d{4})(?![0-9])")
    private val ending = Regex("(?i)ending(?:\\s+(?:with|in))?\\s*:?\\s*[x*•]*\\s*(\\d{4})(?![0-9])")
    private val acct = Regex("(?i)(?:a/c|acct|account|card)(?:\\s+no\\.?)?\\s*:?\\s*(\\d{4})(?![0-9])")
    private const val END = "(?=\\s+(?:on|using|via|for|from|with|ref|refno|txn|upi|avl|bal|if|not|call|has|is|was|successful|dated|thru|through)\\b|[.,;(]|\\s*$)"
    private val info = Regex("(?i)(?:upi|imps|neft)/(?:[a-z0-9]{2,5}/)?\\d{6,}/([^/\\n]{2,30})")
    private val on = Regex("(?i)\\b(?:txn|transaction|payment|purchase)\\s+of\\s+(?:rs\\.?|inr|₹)\\s*[0-9][0-9,.]*\\s+on\\s+([^\\n]{2,40}?)\\s+(?:is|was|has)\\b")
    private val dated = Regex("(?i)\\d{1,2}[-/][a-z0-9]{2,3}[-/]\\d{2,4}\\s+(?:on|at)\\s+([^\\n]{2,40}?)(?=\\.\\s|\\.?\\s*avl|\\.?\\s*$)")
    private val ist = Regex("(?i)\\bist\\s+([^\\n]{2,30}?)\\s+avl")
    private val infoText = Regex("(?i)\\binfo[:\\s-]+(?:upi[-/]\\d+[-/])?([a-z][^\\n.]{2,35}?)(?=\\.\\s|\\s+avl|\\s*$)")
    private val at = Regex("(?i)\\b(?:at|towards)\\s+([^\\n]{2,40}?)$END")
    private val to = Regex("(?i)\\b(?:trf to|paid to|sent to|to)\\s+(?:vpa\\s+)?([^\\n]{2,40}?)$END")
    private val from = Regex("(?i)\\b(?:from|by)\\s+(?:vpa\\s+)?([^\\n]{2,40}?)$END")
    private val gap = Regex("\\s+")
    private val bad = Regex("(?i)^(your|a/c|acct|account|card|the|my|you|this|credit|debit|bank|neft|imps|rtgs|upi|ach|cheque|cash|transfer|slice|customer|ref|date|rs|inr)\\b")

    fun last4(t: String): String? =
        (ending.find(t) ?: mask.find(t) ?: acct.find(t))?.groupValues?.get(1)

    private fun name(raw: String?): String? {
        var s = raw?.trim()?.trimEnd('-', ' ', ':', '/') ?: return null
        if (s.contains('@')) s = s.substringBefore('@')
        if (s.isBlank() || s.all { it.isDigit() || it == ' ' } || bad.containsMatchIn(s) || s.first().isDigit() && s.contains('-')) return null
        s = s.replace(gap, " ").take(28).trim()
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
        if (d == null && c == null && !weak.containsMatchIn(t)) return null
        if ((if (credit) noCr else noDr).containsMatchIn(t)) return null
        val amt = Money.txn(t) ?: return null
        val last4 = last4(t)
        val bank = Brands.bank(r.sender, t)
        val mode = mode(t)
        return if (credit) {
            Event.Credit(amt.paise, amt.currency, last4, bank, pick(from, t) ?: pick(info, t) ?: Brands.shop("", t), mode)
        } else {
            val merchant = pick(info, t) ?: pick(on, t) ?: pick(at, t) ?: pick(dated, t) ?: pick(ist, t) ?: pick(to, t) ?: pick(infoText, t)
            if (mode == Mode.Card && creditCard.containsMatchIn(t) && !debitCard.containsMatchIn(t)) {
                Event.CardSpend(amt.paise, amt.currency, last4, bank, merchant)
            } else {
                Event.Debit(amt.paise, amt.currency, last4, bank, merchant, mode)
            }
        }
    }
}
