package app.companion.core

import java.time.LocalDate

internal object Bill {
    private val statement = Regex("(?i)statement|total\\s+(?:amt|amount)?\\s*due|total\\s+due")
    private val billy = Regex("(?i)\\bbill\\b|\\bdues?\\b(?!\\s+to)|invoice|outstanding")
    private val no = Regex("(?i)declin|returned|insufficient|refund|credited|purchase|reversal|not successful|fail|delayed|download your bill|\\bwill be deducted")
    private val paid = Regex("(?i)payment\\s+(?:of\\s+.{0,20})?(?:received|successful)|thank you for (?:the )?payment|has been paid|(?<!\\bif )(?<!\\bif already )\\bpaid\\b")
    private val total = Regex("(?i)total\\s+(?:amt|amount)?\\s*due|total\\s+due|amount\\s+due|(?:bill|statement)\\s+amount|outstanding(?:\\s+amount)?|amount\\s+payable|bill\\s+of")
    private val min = Regex("(?i)min(?:imum)?\\.?\\s*(?:amt|amount|payment)?\\.?\\s*(?:due)?")

    fun parse(r: Raw, t: String, ref: LocalDate, given: Amt? = null): Event? {
        if (!billy.containsMatchIn(t) || paid.containsMatchIn(t) || no.containsMatchIn(t)) return null
        val minAmt = Money.after(t, min)
        val total = Money.after(t, total) ?: Money.all(t).firstOrNull { it != minAmt && !Regex("(?i)bal|limit").containsMatchIn(t.substring(maxOf(0, it.at - 20), it.at)) } ?: given ?: return null
        val due = Dates.due(t, ref)
        val last4 = Txn.last4(t)
        val bank = Brands.bank(r.sender, t)
        val min2 = minAmt?.paise?.takeIf { it < total.paise }
        return if (statement.containsMatchIn(t) && (last4 != null || bank != null)) {
            Event.Statement(total.paise, min2, due, last4, bank)
        } else {
            Event.Bill(total.paise, total.currency, due, min2, Brands.shop(r.sender, t) ?: bank, last4)
        }
    }
}
