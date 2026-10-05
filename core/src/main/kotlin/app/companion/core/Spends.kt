package app.companion.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class Spent(val kind: String, val category: String?, val merchant: String?, val title: String, val last4: String?, val at: Long, val flow: String?)

object Spends {
    fun counts(s: Spent) =
        (s.kind == Kind.Debit.name || s.kind == Kind.CardSpend.name) && s.flow == null && !CardPay.of(s.kind, s.category, s.merchant ?: s.title)

    fun match(s: Spent, category: String?, merchant: String?, last4: String?, a: LocalDate, b: LocalDate, zone: ZoneId): Boolean {
        val d = Instant.ofEpochMilli(s.at).atZone(zone).toLocalDate()
        return counts(s) && !d.isBefore(a) && !d.isAfter(b) &&
            (category == null || category.equals(s.category, true)) &&
            (merchant == null || s.merchant.orEmpty().contains(merchant, true) || s.title.contains(merchant, true)) &&
            (last4 == null || s.last4 == last4)
    }
}
