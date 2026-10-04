package app.companion.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

sealed interface Query {
    val write: Boolean get() = false

    data class SumSpend(val category: String?, val merchant: String?, val last4: String?, val start: LocalDate, val end: LocalDate) : Query

    data class ListTxns(
        val category: String?,
        val merchant: String?,
        val last4: String?,
        val start: LocalDate,
        val end: LocalDate,
        val limit: Int,
    ) : Query

    data class ListBills(val start: LocalDate, val end: LocalDate, val unpaidOnly: Boolean) : Query

    data class TopMerchants(val start: LocalDate, val end: LocalDate, val n: Int) : Query

    data class CreateReminder(val title: String, val at: Long) : Query {
        override val write get() = true
    }

    data class CreateEvent(val title: String, val start: Long, val end: Long) : Query {
        override val write get() = true
    }
}

class Plan(val queries: List<Query>, val explicit: Boolean)

fun interface Planner {
    fun plan(text: String, now: Long): Plan
}

class Validator(private val now: Long, private val zone: ZoneId) {
    private val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    private val lo = today.minusYears(3)
    private val hi = today.plusYears(1)

    private fun day(d: LocalDate) = !d.isBefore(lo) && !d.isAfter(hi)

    private fun span(a: LocalDate, b: LocalDate) = day(a) && day(b) && !a.isAfter(b)

    private fun at(ms: Long) = day(Instant.ofEpochMilli(ms).atZone(zone).toLocalDate())

    private fun cat(c: String?) = c == null || Category.entries.any { it.label == c }

    private fun last4(s: String?) = s == null || s.matches(Regex("\\d{4}"))

    private fun merchant(m: String?) = m == null || (m.isNotBlank() && m.length <= 40)

    fun check(q: Query): Query? = q.takeIf {
        when (it) {
            is Query.SumSpend -> cat(it.category) && last4(it.last4) && merchant(it.merchant) && span(it.start, it.end)
            is Query.ListTxns -> cat(it.category) && last4(it.last4) && merchant(it.merchant) && span(it.start, it.end) && it.limit in 1..50
            is Query.ListBills -> span(it.start, it.end)
            is Query.TopMerchants -> span(it.start, it.end) && it.n in 1..10
            is Query.CreateReminder -> it.title.isNotBlank() && it.title.length <= 120 && at(it.at)
            is Query.CreateEvent -> it.title.isNotBlank() && it.title.length <= 120 && at(it.start) && it.end > it.start && it.end - it.start <= 24 * 3600_000L
        }
    }

    fun parse(json: String): List<Query> {
        val root = runCatching { Json.parse(json) }.getOrNull()
        val calls = when (root) {
            is List<*> -> root
            is Map<*, *> -> (root["calls"] as? List<*>) ?: listOf(root)
            else -> emptyList()
        }
        return calls.mapNotNull { c -> (c as? Map<*, *>)?.let(::call)?.let(::check) }
    }

    private fun str(a: Map<*, *>, k: String) = (a[k] as? String)?.trim()?.takeIf { it.isNotEmpty() }

    private fun date(a: Map<*, *>, k: String, d: LocalDate) = str(a, k)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: if (a[k] == null) d else null

    private fun moment(a: Map<*, *>, k: String) = str(a, k)?.let { runCatching { LocalDateTime.parse(it).atZone(zone).toInstant().toEpochMilli() }.getOrNull() }

    private fun call(c: Map<*, *>): Query? {
        val name = c["name"] as? String ?: return null
        val a = (c["arguments"] ?: c["args"]) as? Map<*, *> ?: emptyMap<Any, Any>()
        val start = date(a, "start", today.withDayOfMonth(1)) ?: return null
        val end = date(a, "end", today) ?: return null
        val cat = str(a, "category")?.lowercase()
        return when (name) {
            "sumSpend" -> Query.SumSpend(cat, str(a, "merchant"), str(a, "last4") ?: str(a, "card"), start, end)
            "listTxns" -> Query.ListTxns(cat, str(a, "merchant"), str(a, "last4") ?: str(a, "card"), start, end, (a["limit"] as? Number)?.toInt() ?: 10)
            "listBills" -> Query.ListBills(start, if (a["end"] == null) today.plusDays(30) else end, a["unpaidOnly"] as? Boolean ?: true)
            "topMerchants" -> Query.TopMerchants(start, end, (a["n"] as? Number)?.toInt() ?: 5)
            "createReminder" -> Query.CreateReminder(str(a, "title") ?: return null, moment(a, "at") ?: return null)
            "createEvent" -> Query.CreateEvent(str(a, "title") ?: return null, moment(a, "start") ?: return null, moment(a, "end") ?: return null)
            else -> null
        }
    }
}
