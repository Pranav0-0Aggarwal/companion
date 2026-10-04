package app.companion.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class Edit(now: Long, private val zone: ZoneId) {
    private val check = Validator(now, zone)
    private val at = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private fun opt(s: String) = s.trim().takeIf { it.isNotEmpty() }

    private fun day(s: String) = LocalDate.parse(s.trim())

    private fun text(ms: Long) = at.format(Instant.ofEpochMilli(ms).atZone(zone))

    private fun ms(s: String) = LocalDateTime.parse(s.trim(), at).atZone(zone).toInstant().toEpochMilli()

    fun labels(q: Query) = when (q) {
        is Query.SumSpend, is Query.ListTxns -> listOf("Category", "Merchant", "Card last 4", "From", "To")
        is Query.ListBills, is Query.TopMerchants -> listOf("From", "To")
        is Query.CreateReminder -> listOf("Title", "When")
        is Query.CreateEvent -> listOf("Title", "Start", "End")
    }

    fun values(q: Query) = when (q) {
        is Query.SumSpend -> listOf(q.category.orEmpty(), q.merchant.orEmpty(), q.last4.orEmpty(), "${q.start}", "${q.end}")
        is Query.ListTxns -> listOf(q.category.orEmpty(), q.merchant.orEmpty(), q.last4.orEmpty(), "${q.start}", "${q.end}")
        is Query.ListBills -> listOf("${q.start}", "${q.end}")
        is Query.TopMerchants -> listOf("${q.start}", "${q.end}")
        is Query.CreateReminder -> listOf(q.title, text(q.at))
        is Query.CreateEvent -> listOf(q.title, text(q.start), text(q.end))
    }

    fun apply(q: Query, v: List<String>): Query? = runCatching {
        when (q) {
            is Query.SumSpend -> q.copy(category = opt(v[0])?.lowercase(), merchant = opt(v[1]), last4 = opt(v[2]), start = day(v[3]), end = day(v[4]))
            is Query.ListTxns -> q.copy(category = opt(v[0])?.lowercase(), merchant = opt(v[1]), last4 = opt(v[2]), start = day(v[3]), end = day(v[4]))
            is Query.ListBills -> q.copy(start = day(v[0]), end = day(v[1]))
            is Query.TopMerchants -> q.copy(start = day(v[0]), end = day(v[1]))
            is Query.CreateReminder -> q.copy(title = v[0].trim(), at = ms(v[1]))
            is Query.CreateEvent -> q.copy(title = v[0].trim(), start = ms(v[1]), end = ms(v[2]))
        }
    }.getOrNull()?.let(check::check)
}
