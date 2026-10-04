package app.companion.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class Range(val from: LocalDate, val to: LocalDate) {
    companion object {
        fun of(spec: String, today: LocalDate): Range? {
            val s = spec.trim().lowercase()
            return when (s) {
                "today" -> Range(today, today)
                "yesterday" -> Range(today.minusDays(1), today.minusDays(1))
                "week" -> Range(today.minusDays(6), today)
                "month" -> Range(today.withDayOfMonth(1), today)
                "last_month" -> today.minusMonths(1).let { Range(it.withDayOfMonth(1), it.withDayOfMonth(it.lengthOfMonth())) }
                "year" -> Range(today.withDayOfYear(1), today)
                "all" -> Range(LocalDate.of(2000, 1, 1), today.plusYears(1))
                "next_week" -> Range(today, today.plusDays(7))
                "next_month" -> Range(today, today.plusDays(30))
                else -> runCatching {
                    val (a, b) = s.split("..")
                    Range(LocalDate.parse(a), LocalDate.parse(b)).takeIf { !it.to.isBefore(it.from) }
                }.getOrNull()
            }
        }
    }
}

sealed interface Call {
    val write: Boolean get() = false

    data class Search(val query: String, val range: Range) : Call
    data class Spend(val category: String?, val merchant: String?, val card: String?, val range: Range) : Call
    data class Bills(val range: Range) : Call
    data object Tasks : Call
    data class Remind(val title: String, val at: Long) : Call {
        override val write get() = true
    }
    data class Cal(val title: String, val start: Long, val end: Long) : Call {
        override val write get() = true
    }
}

sealed interface Outcome {
    data class Read(val call: Call) : Outcome
    data class Propose(val call: Call, val tainted: Boolean) : Outcome
    data class Reject(val why: String) : Outcome
}

class Router(private val today: LocalDate, private val zone: ZoneId) {
    private var tainted = false

    fun seen() {
        tainted = true
    }

    fun handle(name: String, args: Map<String, Any?>): Outcome {
        val call = parse(name, args) ?: return Outcome.Reject("bad call")
        return if (call.write) Outcome.Propose(call, tainted) else Outcome.Read(call)
    }

    private fun str(a: Map<String, Any?>, k: String, max: Int): String? = (a[k] as? String)?.trim()?.takeIf { it.isNotEmpty() && it.length <= max }

    private fun range(a: Map<String, Any?>) = Range.of((a["range"] as? String) ?: "month", today)

    private fun moment(a: Map<String, Any?>, k: String): Long? = runCatching {
        LocalDateTime.parse((a[k] as String).trim()).atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()

    private fun parse(name: String, a: Map<String, Any?>): Call? = when (name) {
        "searchItems" -> str(a, "query", 80)?.let { q -> range(a)?.let { Call.Search(q, it) } }
        "sumSpend" -> {
            val cat = (a["category"] as? String)?.trim()?.lowercase()
            val card = (a["card"] as? String)?.trim()
            if ((cat != null && Category.entries.none { it.label == cat }) || (card != null && !card.matches(Regex("\\d{4}")))) {
                null
            } else {
                range(a)?.let { Call.Spend(cat, str(a, "merchant", 40), card, it) }
            }
        }
        "listBills" -> range(a)?.let { Call.Bills(it) }
        "listTasks" -> Call.Tasks
        "createReminder" -> str(a, "title", 120)?.let { t -> moment(a, "at")?.let { Call.Remind(t, it) } }
        "createEvent" -> str(a, "title", 120)?.let { t ->
            val s = moment(a, "start")
            val e = moment(a, "end")
            if (s != null && e != null && e > s) Call.Cal(t, s, e) else null
        }
        else -> null
    }
}
