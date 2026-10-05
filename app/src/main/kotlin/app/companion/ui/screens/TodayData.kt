package app.companion.ui.screens

import android.Manifest
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.core.Cycle
import app.companion.core.Margin
import app.companion.core.Meeting
import app.companion.core.Meets
import app.companion.core.Safe
import app.companion.core.Timeline
import app.companion.core.Track
import app.companion.data.Eaten
import app.companion.data.Item
import app.companion.data.Profile
import app.companion.data.Repo
import app.companion.data.Task
import app.companion.data.credit
import app.companion.data.dueDate
import app.companion.data.moved
import app.companion.sl
import app.companion.system.Cals
import app.companion.system.Ping
import app.companion.system.Prefs
import app.companion.ui.dateOf
import app.companion.ui.daysTo
import app.companion.ui.has
import app.companion.ui.inDays
import app.companion.ui.inr
import app.companion.ui.zone
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class Ev(val key: String, val at: Long?, val rank: Int = 0) {
    class Meal(val e: Eaten, val paid: Item?) : Ev("m${e.meal.id}", e.meal.at)
    class Spend(val i: Item) : Ev("s${i.id}", i.at)
    class Meet(val m: Meeting, val clash: String?) : Ev("e${m.id}:${m.start}", m.start)
    class Order(val o: Ping.Order) : Ev("o${o.key}", o.at)
    class Bill(val b: Item, val paid: Boolean) : Ev("b${b.id}", null)
    class Chore(val t: Task) : Ev("t${t.id}", t.remindAt)
    class Review(val n: Int) : Ev("review", null, -1)
}

class Fig(val label: String, val figure: String, val sub: String, val over: Boolean)

private fun whole(paise: Long) = inr(Math.round(paise / 100.0) * 100)

fun Tl.fig(live: Boolean): Fig {
    val m = safe
    val pay = payIn?.let { " · payday ${inDays(it)}" }.orEmpty()
    return when (m) {
        is Margin.Over -> Fig("Over budget", "${whole(m.by)} over", "${whole(month)} spent of ${whole(budget)} budget$pay", true)
        is Margin.Safe -> Fig("Safe to spend", whole(m.perDay), "a day$pay", false)
        null -> Fig(if (live) "Spent today" else "Spent", inr(spent), "$n ${if (n == 1) "payment" else "payments"}", false)
    }
}

class Tl(val items: List<Ev>, val soon: List<Ev>, val spent: Long, val n: Int, val kcal: Int, val safe: Margin?, val payIn: Int?, val month: Long, val budget: Long)

private class Ext(val meets: List<Meeting> = emptyList(), val orders: List<Ping.Order> = emptyList(), val asked: Boolean = false)

private const val DAY = 86_400_000L

private fun Task.day(): LocalDate? = remindAt?.let(::dateOf) ?: due?.let(LocalDate::ofEpochDay)

private fun Ev.day(): LocalDate = when (this) {
    is Ev.Bill -> b.dueDate!!
    is Ev.Chore -> t.day()!!
    is Ev.Meet -> dateOf(m.start)
    else -> LocalDate.MIN
}

private suspend fun orders(c: Context, repo: Repo, day: LocalDate, now: Long): List<Ping.Order> {
    if (day == dateOf(now)) return Ping.orders(c, 0, now)
    val z = zone()
    val a = day.atStartOfDay(z).toInstant().toEpochMilli()
    return repo.deliveriesSince(a).filter { it.at < a + DAY }.groupBy { Track.key(it.merchant, it.at, z) }.mapNotNull { (k, l) ->
        val by = l.mapNotNull { i -> Track.stage(i.title)?.let { it to i } }
        val st = Track.latest(by.map { it.first }) ?: return@mapNotNull null
        val i = by.last { it.first == st }.second
        Ping.Order(k, i.merchant, st, i.at, i.id, i.ping)
    }
}

private fun meets(c: Context, from: Long, to: Long): List<Meeting> {
    if (!c.has(Manifest.permission.READ_CALENDAR)) return emptyList()
    val o = Prefs.meets(c)
    return runCatching { Cals.meetings(c, from, to, o).filter { Meets.wanted(it, o) } }.getOrDefault(emptyList())
}

@Composable
fun rememberTl(day: LocalDate, today: LocalDate, now: Long): Tl {
    val c = LocalContext.current
    val repo = c.sl.repo
    val prof by repo.profile.collectAsStateWithLifecycle(Profile())
    val money by repo.money.collectAsStateWithLifecycle(emptyList())
    val bills by repo.bills.collectAsStateWithLifecycle(emptyList())
    val paid by repo.paid.collectAsStateWithLifecycle(emptyList())
    val tasks by repo.tasks.collectAsStateWithLifecycle(emptyList())
    val asks by repo.asks.collectAsStateWithLifecycle(emptyList())
    val meals by remember(day) { repo.life.eatenOn(day) }.collectAsStateWithLifecycle(emptyList())
    val ext by produceState(Ext(), day, now / 300_000) {
        value = withContext(Dispatchers.IO) {
            val a = day.atStartOfDay(zone()).toInstant().toEpochMilli()
            Ext(meets(c, a, a + if (day == today) 3 * DAY else DAY), orders(c, repo, day, now), c.has(Manifest.permission.READ_CALENDAR))
        }
    }
    return remember(day, today, prof, money, bills, paid, tasks, asks, meals, ext) { build(day, today, prof, money, bills, paid, tasks, asks, meals, ext) }
}

private fun build(day: LocalDate, today: LocalDate, prof: Profile, money: List<Item>, bills: List<Item>, paid: List<Item>, tasks: List<Task>, asks: List<Item>, meals: List<Eaten>, ext: Ext): Tl {
    val live = day == today
    val here = money.filter { dateOf(it.at) == day }
    val taken = meals.filter { it.meal.src == "order" }.mapNotNull { m -> here.firstOrNull { it.at == m.meal.at }?.id }.toSet()
    val clashes = Meets.clashes(ext.meets).flatMap { listOf(it.a to it.b.title, it.b to it.a.title) }.toMap()
    val evs = buildList {
        meals.forEach { e -> add(Ev.Meal(e, if (e.meal.src == "order") here.firstOrNull { it.at == e.meal.at } else null)) }
        here.filter { it.id !in taken }.forEach { add(Ev.Spend(it)) }
        ext.meets.filter { dateOf(it.start) == day }.forEach { add(Ev.Meet(it, clashes[it])) }
        ext.orders.filter { dateOf(it.at) == day }.forEach { add(Ev.Order(it)) }
        bills.filter { b -> b.dueDate?.let { it == day || live && it < day } == true }.forEach { add(Ev.Bill(it, false)) }
        paid.filter { it.dueDate == day }.forEach { add(Ev.Bill(it, true)) }
        tasks.filter { !it.gone && it.day() == day }.forEach { add(Ev.Chore(it)) }
        if (live && asks.isNotEmpty()) add(Ev.Review(asks.size))
    }
    val soon = if (!live) emptyList() else buildList {
        bills.filter { b -> b.dueDate?.let { daysTo(it, day) in 1..7 } == true }.forEach { add(Ev.Bill(it, false)) }
        tasks.filter { t -> !t.gone && !t.done && t.day()?.let { daysTo(it, day) in 1..7 } == true }.forEach { add(Ev.Chore(it)) }
        ext.meets.filter { dateOf(it.start) > day }.forEach { add(Ev.Meet(it, null)) }
    }.sortedWith(compareBy<Ev>({ it.day() }, { it.at ?: 0L }))
    val spent = here.spends().sumOf { it.paise }
    val n = here.count { !it.credit && !it.moved }
    val pd = prof.payDay?.let { Cycle.payday(it, day) }
    val month = money.filter { dateOf(it.at).let { d -> d.year == day.year && d.month == day.month } }.spends().sumOf { it.paise }
    val safe = if (live && pd != null) Safe.room(prof.budget, month, Safe.owed(bills.map { it.dueDate to it.paise }, pd), Safe.days(day, pd)) else null
    return Tl(Timeline.order(evs, { it.at }, { it.rank }), soon, spent, n, meals.sumOf { it.kcal }.toInt(), safe, pd?.let { daysTo(it, day) }, month, prof.budget ?: 0)
}
