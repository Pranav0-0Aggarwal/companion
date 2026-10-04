package app.companion.core

import java.time.Instant
import java.time.ZoneId

enum class Field { OtpCode, Amount, Merchant, Due, Last4 }

interface Extractor {
    fun extract(raw: Raw, want: Set<Field>): Map<Field, String>
}

object NoExtractor : Extractor {
    override fun extract(raw: Raw, want: Set<Field>) = emptyMap<Field, String>()
}

fun Raw.text() = listOf(title, body).filter { it.isNotBlank() }.joinToString("\n")

object Gaps {
    private const val NAME = "(?!(?:${Txn.BAD}|ac)\\b)\\p{L}{2}"
    private val digit = Regex("\\d")
    private val cur = Regex("(?i)(?<![a-z])(?:rs\\.?|inr|₹|usd|\\$|eur|€|gbp|£)|\\b(?:amt|amount)\\b")
    private val verb = Regex("(?i)\\b(?:debited|credited|spent|withdrawn|paid|sent|received|refund(?:ed)?|deposited)\\b")
    private val billy = Regex("(?i)\\bbill\\b|\\bdues?\\b|statement|stmt|invoice|outstanding")
    private val payee = Regex("(?i)\\b(?:at|towards|vpa|info|merchant|payee|beneficiary|(?:paid|sent|trf|transferred)\\s+to)\\b[\\s:/-]*$NAME")
    private val card = Regex("(?i)\\b(?:card|a/c|acct|account)\\b[^\\n]{0,30}?(?<![\\d.,/-])\\d{4}(?!\\d|[.,/-]\\d)")
    private val payer = Regex("(?i)\\b(?:from|by|payer|sender|vpa|info)\\b[\\s:/-]*$NAME")

    private fun open(e: Event) = e == Event.Alert || e == Event.Unknown

    fun of(raw: Raw, e: Event, zone: ZoneId = ZoneId.systemDefault()): Set<Field> {
        if (raw.source == Source.Wa || raw.source == Source.Ig) return emptySet()
        val t = raw.text()
        val ref = Instant.ofEpochMilli(raw.at).atZone(zone).toLocalDate()
        val amount = open(e) && Money.all(t).isEmpty() && digit.containsMatchIn(t) && (cur.containsMatchIn(t) || verb.containsMatchIn(t))
        val dated = Dates.first(t, ref) != null
        return buildSet {
            if ((open(e) || e is Event.Delivery) && Otp.maybe(raw, t)) add(Field.OtpCode)
            if (amount) add(Field.Amount)
            val name = when (e) {
                is Event.Move -> e.merchant == null && (if (e is Event.Credit) payer else payee).containsMatchIn(t)
                is Event.Bill -> e.biller == null
                else -> false
            }
            if (name) add(Field.Merchant)
            val due = when (e) {
                is Event.Bill -> e.due == null
                is Event.Statement -> e.due == null
                else -> amount && billy.containsMatchIn(t)
            }
            if (due && dated) add(Field.Due)
            val bare = when (e) {
                is Event.Move -> e.last4 == null
                is Event.Bill -> e.last4 == null
                is Event.Statement -> e.last4 == null
                else -> false
            }
            if (bare && card.containsMatchIn(t)) add(Field.Last4)
        }
    }

    fun needs(raw: Raw, v: Verdict, zone: ZoneId = ZoneId.systemDefault()) = v is Verdict.Unsure || of(raw, v.event, zone).isNotEmpty()
}
