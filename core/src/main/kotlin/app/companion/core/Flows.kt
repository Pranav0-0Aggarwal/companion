package app.companion.core

enum class Flow { Self, CardBill, Invest }

object Flows {
    private val i = RegexOption.IGNORE_CASE
    private val bill = listOf(
        Regex("\\bpayment\\b.{0,80}\\breceived\\b.{0,80}\\b(?:credit\\s*card|cc)\\b", i),
        Regex("\\breceived\\b.{0,40}\\b(?:towards|for)\\b.{0,30}\\bcredit\\s*card\\b", i),
        Regex("\\b(?:towards|for)\\b(?:\\s+\\S+){0,4}\\s+credit\\s*card\\b", i),
        Regex("\\b(?:credit\\s*card|cc)\\s*(?:bill\\s*)?(?:payment|pymt|pmt)\\b", i),
        Regex("\\b(?:cc|credit\\s*card)\\b.{0,40}\\b(?:billdesk|bill\\s*desk|cred(?:\\s*club)?|bbps)\\b", i),
        Regex("\\b(?:billdesk|bill\\s*desk|cred(?:\\s*club)?)\\b.{0,40}\\b(?:cc|credit\\s*card)\\b", i),
        Regex("\\bcredclub\\b|\\bcred\\.club\\b", i),
    )
    private val paid = Regex("\\bpayment\\b.{0,80}\\breceived\\b", i)
    private val self = Regex("\\bown\\s+(?:a/?c|acc(?:oun)?ts?)\\b|\\bself[\\s-]*(?:transfer|a/?c|account)\\b|\\btransfer(?:red)?\\s+to\\s+self\\b|\\bto\\s+self\\b", i)
    private val invest = Regex(
        "\\bsip\\b|\\bmutual\\s*funds?\\b|\\bmf\\b|\\bnav\\b|\\bunits?\\s+allotted\\b|\\bzerodha\\b|\\bgroww\\b|\\bkuvera\\b|\\bcoin\\b|\\biccl\\b|" +
            "\\bnse\\s+clearing\\b|\\bbse\\s*star(?:\\s*mf)?\\b|\\b(?:ppf|nps)\\b.{0,40}\\bcontribut|\\bcontribut.{0,40}\\b(?:ppf|nps)\\b",
        i,
    )

    private fun side(dir: String) = Regex(
        "\\b(?:$dir)\\s+(?:your\\s+|my\\s+|the\\s+)?(?:(?:a/?c|acct|account|card)\\b\\.?\\s*(?:no\\.?\\s*|number\\s*|ending\\s*(?:in|with)?\\s*)?(?:x+|\\*+|•+)?|(?:x{2,}|\\*+|•+))(\\d{4})\\b",
        i,
    )

    private val out = side("to|towards|into")
    private val into = side("from|by")

    fun own(last4s: Iterable<String?>) = last4s.filterNotNull().filter { it.length == 4 && it.all(Char::isDigit) }.toSet()

    fun counterparty(e: Event.Move, text: String): Set<String> =
        (if (e is Event.Credit) into else out).findAll(text).map { it.groupValues[1] }.filter { it != e.last4 }.toSet()

    fun of(e: Event.Move, text: String, own: Set<String> = emptySet()): Flow? {
        if (e !is Event.CardSpend) {
            if (bill.any { it.containsMatchIn(text) } || (e is Event.Credit && e.mode == Mode.Card && paid.containsMatchIn(text))) return Flow.CardBill
            if (self.containsMatchIn(text) || counterparty(e, text).any { it in own }) return Flow.Self
        }
        return if (invest.containsMatchIn(text)) Flow.Invest else null
    }

    fun income(flow: String?) = flow != Flow.Self.name && flow != Flow.CardBill.name
}
