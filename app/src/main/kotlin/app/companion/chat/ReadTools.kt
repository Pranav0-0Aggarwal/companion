package app.companion.chat

import android.Manifest
import android.content.Context
import app.companion.core.Best
import app.companion.core.Card
import app.companion.core.Cmp
import app.companion.core.DueRow
import app.companion.core.Dues
import app.companion.core.Fact
import app.companion.core.Filt
import app.companion.core.Bar
import app.companion.core.Ledgers
import app.companion.core.Led
import app.companion.core.Meets
import app.companion.core.MealSum
import app.companion.core.MsgRow
import app.companion.core.NeedRow
import app.companion.core.Phrase
import app.companion.core.Plastic
import app.companion.core.Rs
import app.companion.core.Row
import app.companion.core.Senders
import app.companion.core.Shape
import app.companion.core.Snip
import app.companion.core.Spends
import app.companion.core.ToolOut
import app.companion.core.ToolSpecs
import app.companion.core.TripSum
import app.companion.core.Weigh
import app.companion.data.Item
import app.companion.data.Repo
import app.companion.data.dueDate
import app.companion.data.spent
import app.companion.system.Cals
import app.companion.system.Prefs
import app.companion.ui.clock
import app.companion.ui.dateOf
import app.companion.ui.has
import app.companion.ui.shortDay
import app.companion.ui.span
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.first

private val zone get() = ZoneId.systemDefault()

private const val DAY = 86_400_000L

internal fun Map<String, Any?>.n(k: String) = (this[k] as? Number)?.toInt()

private fun Map<String, Any?>.paise(k: String) = d(k)?.let { Math.round(it * 100) }

private fun String.cap() = replaceFirstChar(Char::uppercase)

private fun period(a: Map<String, Any?>, k: String, def: String? = null) = (a.s(k) ?: def)?.let { Phrase(System.currentTimeMillis(), zone).period(it) }

private suspend fun Repo.spends() = money.first().filter { it.currency == "INR" && Spends.counts(it.spent()) }.map { Led(it.id, it.at, it.merchant ?: it.title, it.category, it.last4, it.bank, it.paise) }

private fun label(merchant: String?, category: String?, from: LocalDate, to: LocalDate) =
    listOfNotNull(merchant, category?.cap()).joinToString(" · ").ifEmpty { "All spending" } + " · " + span(from, to)

private fun Item.by(s: String) = merchant.orEmpty().contains(s, true) || title.contains(s, true)

private fun more(n: Int, shown: Int) = (n - shown).coerceAtLeast(0)

class SearchMessages(private val repo: Repo, private val show: (Card) -> Unit) : Base(ToolSpecs.searchMessages) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val q = a.s("q") ?: return ToolOut.Fail("q")
        val from = a.s("from")
        val since = a.n("days")?.takeIf { it > 0 }?.let { System.currentTimeMillis() - it * DAY }
        val hits = repo.search(q).first().filter { since == null || it.at >= since }
        val by = repo.senders(hits.map { it.id })
        fun who(i: Item) = by[i.id]?.let { s -> if (i.src == "Sms") Senders.title(s) else s } ?: i.bank ?: i.title
        val rows = hits.filter { from == null || who(it).contains(from, true) || by[it.id].orEmpty().contains(from, true) || it.by(from) }
            .map { MsgRow(it.id, who(it), it.at, Snip.of(it.body?.takeIf { b -> b.isNotBlank() } ?: "${it.title} ${it.note}", q)) }
        if (rows.isEmpty()) return ToolOut.Ok("No messages match.")
        val shown = rows.take(Shape.MSGS)
        show(Card.Facts("Messages · ${rows.size}", null, shown.map { Fact(it.from, it.snip, shortDay(dateOf(it.at))) }, more(rows.size, shown.size)))
        return ToolOut.Ok(Shape.msgs(rows.size, rows, zone))
    }
}

class Ledger(private val repo: Repo, private val show: (Card) -> Unit) : Base(ToolSpecs.ledger) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val (from, to) = period(a, "period", "this month") ?: return ToolOut.Fail("I could not read that period")
        val p = Ledgers.run(repo.spends(), Filt(a.s("merchant"), a.s("category"), a.s("card"), a.paise("min"), a.paise("max"), from, to), zone)
        if (p.n == 0) return ToolOut.Ok("No spends match.")
        show(Card.Facts(label(a.s("merchant"), a.s("category"), from, to), Rs.of(p.total), p.rows.map { Fact(it.who, listOfNotNull(shortDay(dateOf(it.at)), it.cat).joinToString(" · "), Rs.of(it.paise)) }, more(p.n, p.rows.size), "ledger"))
        return ToolOut.Ok(Shape.ledger(p, zone))
    }
}

class TopMerchants(private val repo: Repo, private val show: (Card) -> Unit) : Base(ToolSpecs.topMerchants) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val (from, to) = period(a, "period", "this month") ?: return ToolOut.Fail("I could not read that period")
        val p = Ledgers.top(repo.spends(), a.n("n") ?: 5, from, to, zone)
        if (p.n == 0) return ToolOut.Ok("No spends in that period.")
        show(Card.Facts("Top merchants · ${span(from, to)}", Rs.of(p.total), p.rows.map { Fact(it.who, "${it.n} ${if (it.n == 1) "payment" else "payments"}", Rs.of(it.paise)) }, more(p.n, p.rows.size), "ledger"))
        return ToolOut.Ok(Shape.top(p))
    }
}

class Compare(private val repo: Repo, private val show: (Card) -> Unit) : Base(ToolSpecs.compare) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val pa = a.s("period_a") ?: return ToolOut.Fail("period_a")
        val pb = a.s("period_b") ?: return ToolOut.Fail("period_b")
        val (x, y) = period(a, "period_a") ?: return ToolOut.Fail("I could not read $pa")
        val (u, v) = period(a, "period_b") ?: return ToolOut.Fail("I could not read $pb")
        val all = repo.spends()
        fun sum(from: LocalDate, to: LocalDate) = Ledgers.run(all, Filt(a.s("merchant"), a.s("category"), null, null, null, from, to), zone)
        val one = sum(x, y)
        val two = sum(u, v)
        show(Card.Spend(label(a.s("merchant"), a.s("category"), x, y), one.total, one.n, emptyList(), Bar(span(u, v), two.total)))
        return ToolOut.Ok(Shape.compare(pa, pb, Cmp(one.total, one.n, two.total, two.n)))
    }
}

class NeedsYou(private val repo: Repo, private val show: (Card) -> Unit) : Base(ToolSpecs.needsYou) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val rows = repo.needing((a.n("n") ?: 5).coerceIn(1, 8))
        val n = repo.needCount()
        if (rows.isEmpty()) return ToolOut.Ok("Nothing needs you right now.")
        show(Card.Facts("Needs you · $n", null, rows.map { Fact(it.title, "${it.kind} · ${shortDay(dateOf(it.at))}", if (it.paise > 0) Rs.of(it.paise) else "") }, more(n, rows.size), "inbox"))
        return ToolOut.Ok(Shape.needs(n, rows.map { NeedRow(it.id, it.kind, it.title, it.paise, it.at, it.state) }, zone))
    }
}

internal fun Item.dueRow(paid: Boolean) = DueRow(id, title, merchant ?: bank, last4, dueDate, paise, minPaise, paid)

internal suspend fun Repo.dues(): List<DueRow> = cycles().map { it.first().dueRow(false) } + paid.first().map { it.dueRow(true) }

class BillCycle(private val repo: Repo, private val show: (Card) -> Unit) : Base(ToolSpecs.billCycle) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val rows = Dues.order(Dues.pick(repo.dues(), listOfNotNull(a.s("card"), a.s("biller")).joinToString(" "))).take(8)
        if (rows.isEmpty()) return ToolOut.Ok("No matching bills.")
        val today = LocalDate.now(zone)
        show(Card.Bills("Bills", rows.filter { !it.paid }.sumOf { it.paise }, rows.map { r -> Row(r.title, listOfNotNull(Dues.status(r, today), r.due?.let { shortDay(it) }, r.min?.takeIf { !r.paid }?.let { "min ${Rs.of(it)}" }).joinToString(" · "), r.paise) }))
        return ToolOut.Ok(Shape.dues(rows, today))
    }
}

class BestCard(private val repo: Repo, private val show: (Card) -> Unit) : Base(ToolSpecs.bestCard) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val cards = repo.cards.first().map { Plastic((listOf(it.bank.removeSuffix(" Bank"), it.nick).filter(String::isNotBlank) + "··${it.last4}").joinToString(" "), it.stmtDay, it.dueDay) }
        if (cards.isEmpty()) return ToolOut.Ok("No cards added yet.")
        val w = Best.rank(cards, LocalDate.now(zone))
        show(Card.Facts("Best card today", w.first().name, w.take(8).map { Fact(it.name, "pay by ${shortDay(it.due)}", "${it.days} days") }, open = "cards"))
        return ToolOut.Ok(Shape.best(w))
    }
}

class Meals(private val repo: Repo, private val foods: Foods, private val show: (Card) -> Unit) : Base(ToolSpecs.meals) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val day = foods.wen(a.s("date").orEmpty()).day
        val ms = repo.life.eaten(day)
        if (ms.isEmpty()) return ToolOut.Ok("Nothing logged for $day.")
        val goal = foods.target()
        val kcal = Math.round(ms.sumOf { it.kcal }).toInt()
        show(Card.Day(day.toEpochDay(), kcal, goal, ms.filter { it.items.isNotEmpty() }.map { Bar(it.meal.slot, Math.round(it.kcal)) }))
        return ToolOut.Ok(Shape.meals(day, kcal, goal, ms.map { m -> MealSum(m.meal.slot, Math.round(m.kcal).toInt(), m.items.map { it.name }) }))
    }
}

class WeightTrend(private val repo: Repo, private val show: (Card) -> Unit) : Base(ToolSpecs.weightTrend) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val weeks = (a.n("weeks") ?: 8).coerceIn(1, 52)
        val v = Weigh.view(repo.life.weights(400).map { LocalDate.ofEpochDay(it.day) to it.kg }, weeks, LocalDate.now(zone)) ?: return ToolOut.Ok("No weights logged in that time.")
        val label = listOfNotNull("Weight", "$weeks ${if (weeks == 1) "week" else "weeks"}", v.perWeek?.let { String.format(Locale.US, "%+.1f kg a week", it) }).joinToString(" · ")
        show(Card.Facts(label, String.format(Locale.US, "%.1f kg", v.last), v.weeks.map { Fact("Week of ${shortDay(it.from)}", "", String.format(Locale.US, "%.1f kg", it.kg)) }))
        return ToolOut.Ok(Shape.weight(v))
    }
}

class Meetings(private val c: Context, private val show: (Card) -> Unit) : Base(ToolSpecs.meetings) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        if (!c.has(Manifest.permission.READ_CALENDAR)) return ToolOut.Ok("Calendar access is not allowed, so I cannot see your meetings.")
        val ph = Phrase(System.currentTimeMillis(), zone)
        val day = ph.slot(a.s("day") ?: "today")?.date ?: ph.period(a.s("day") ?: "today", true)?.first ?: return ToolOut.Fail("I could not read that day")
        val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val o = Prefs.meets(c)
        val rows = runCatching { Cals.meetings(c, from, from + DAY, o).filter { Meets.wanted(it, o) } }.getOrDefault(emptyList())
        if (rows.isEmpty()) return ToolOut.Ok("No meetings on $day.")
        show(Card.Facts("Meetings · ${shortDay(day)}", null, rows.take(8).map { Fact(it.title.ifBlank { "Meeting" }, "${clock(it.start)} to ${clock(it.end)}", Meets.source(it).orEmpty()) }, more(rows.size, 8), "today"))
        return ToolOut.Ok(Shape.meets(day, rows, zone))
    }
}

class Trips(private val repo: Repo, private val show: (Card) -> Unit) : Base(ToolSpecs.trips) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val all = repo.life.trips()
        if (all.isEmpty()) return ToolOut.Ok("No trips yet.")
        val rows = all.take(8).map { t ->
            val s = repo.life.summary(t)
            TripSum(t.id, t.name, dateOf(t.start), dateOf(t.end), t.active, s.total, s.days)
        }
        show(Card.Facts("Trips · ${all.size}", null, rows.map { Fact(it.name, "${shortDay(it.from)} to ${shortDay(it.to)}", Rs.of(it.total)) }, more(all.size, rows.size), "trips"))
        return ToolOut.Ok(Shape.trips(rows))
    }
}
