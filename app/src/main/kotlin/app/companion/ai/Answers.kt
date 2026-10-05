package app.companion.ai

import app.companion.core.Query
import app.companion.core.Spends
import app.companion.data.Item
import app.companion.data.Repo
import app.companion.data.spent
import app.companion.data.dueDate
import app.companion.ui.clock
import app.companion.ui.dateOf
import app.companion.ui.dayLabel
import app.companion.ui.shortDay
import app.companion.ui.span
import app.companion.ui.zone
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

data class Drill(val category: String?, val merchant: String?, val last4: String?, val start: LocalDate, val end: LocalDate)

class Line(val title: String, val sub: String, val paise: Long, val date: LocalDate, val drill: Drill? = null)

class Answer(
    val says: String,
    val query: Query,
    val total: Long?,
    val count: Int?,
    val lines: List<Line>,
    val drill: Drill?,
    val proposal: Query? = null,
)

object DrillBox {
    val pending = MutableStateFlow<Drill?>(null)
}

object Answers {
    private fun line(i: Item) = Line(
        i.title,
        listOfNotNull(i.category, i.bank?.removeSuffix(" Bank"), i.last4?.let { "··$it" }).joinToString(" · "),
        i.paise,
        dateOf(i.at),
    )

    private fun label(category: String?, merchant: String?, last4: String?) =
        listOfNotNull(merchant, category?.replaceFirstChar(Char::uppercase), last4?.let { "card ··$it" }).joinToString(" · ").ifEmpty { "All spending" }

    fun says(q: Query) = when (q) {
        is Query.SumSpend -> "${label(q.category, q.merchant, q.last4)} · ${span(q.start, q.end)}"
        is Query.ListTxns -> "${label(q.category, q.merchant, q.last4)} · latest ${q.limit} · ${span(q.start, q.end)}"
        is Query.ListBills -> "Bills due${if (q.unpaidOnly) " · unpaid" else ""} · ${span(q.start, q.end)}"
        is Query.TopMerchants -> "Top ${q.n} merchants · ${span(q.start, q.end)}"
        is Query.CreateReminder -> "Reminder · ${q.title} · ${dayLabel(dateOf(q.at))} ${clock(q.at)}"
        is Query.CreateEvent -> "Event · ${q.title} · ${dayLabel(dateOf(q.start))} ${clock(q.start)} to ${clock(q.end)}"
    }

    private suspend fun rows(repo: Repo, category: String?, merchant: String?, last4: String?, a: LocalDate, b: LocalDate) =
        repo.money.first().filter { Spends.match(it.spent(), category, merchant, last4, a, b, zone()) }

    suspend fun run(q: Query, repo: Repo): Answer = when (q) {
        is Query.SumSpend -> rows(repo, q.category, q.merchant, q.last4, q.start, q.end).let { r ->
            Answer(says(q), q, r.sumOf { it.paise }, r.size, r.sortedByDescending { it.paise }.take(3).map(::line), Drill(q.category, q.merchant, q.last4, q.start, q.end))
        }
        is Query.ListTxns -> rows(repo, q.category, q.merchant, q.last4, q.start, q.end).let { r ->
            Answer(says(q), q, r.sumOf { it.paise }, r.size, r.take(q.limit).map(::line), Drill(q.category, q.merchant, q.last4, q.start, q.end))
        }
        is Query.ListBills -> repo.billsNow().filter { b -> b.dueDate?.let { !it.isBefore(q.start) && !it.isAfter(q.end) } == true }.let { r ->
            Answer(says(q), q, r.sumOf { it.paise }, r.size, r.take(8).map { Line(it.title, "due ${shortDay(it.dueDate!!)}", it.paise, it.dueDate!!) }, null)
        }
        is Query.TopMerchants -> rows(repo, null, null, null, q.start, q.end).filter { it.merchant != null }.groupBy { it.merchant!! }.let { g ->
            val top = g.entries.sortedByDescending { e -> e.value.sumOf { it.paise } }.take(q.n)
            Answer(
                says(q), q, top.sumOf { e -> e.value.sumOf { it.paise } }, g.size,
                top.map { e -> Line(e.key, "${e.value.size} payment${if (e.value.size == 1) "" else "s"}", e.value.sumOf { it.paise }, q.end, Drill(null, e.key, null, q.start, q.end)) },
                Drill(null, null, null, q.start, q.end),
            )
        }
        is Query.CreateReminder -> Answer(says(q), q, null, null, emptyList(), null, q)
        is Query.CreateEvent -> Answer(says(q), q, null, null, emptyList(), null, q)
    }
}
