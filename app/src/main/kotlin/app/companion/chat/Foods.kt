package app.companion.chat

import android.content.Context
import app.companion.core.Answer
import app.companion.core.Ask
import app.companion.core.Bar
import app.companion.core.Card
import app.companion.core.BrandMenus
import app.companion.core.Clarify
import app.companion.core.FoodDb
import app.companion.core.Plates
import app.companion.core.Hit
import app.companion.core.MealLine
import app.companion.core.Meal
import app.companion.core.MealTimes
import app.companion.core.Req
import app.companion.core.Resolver
import app.companion.core.Targets
import app.companion.core.ToolOut
import app.companion.core.When
import app.companion.core.Wen
import app.companion.data.Eaten
import app.companion.data.Repo
import app.companion.system.Health
import app.companion.system.Nudges
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

class Foods(private val c: Context, private val repo: Repo) {
    private val db by lazy { FoodDb.parse(asset("food_db.json")) }
    private val menus by lazy { BrandMenus.parse(asset("brand_menus.json")) }
    private val zone get() = ZoneId.systemDefault()

    private class Hold(val w: Wen, val src: String, val note: String?, val order: Long?, val hits: List<Hit>, val ask: Ask, val rest: List<Req>)

    @Volatile private var hold: Hold? = null

    @Volatile var order: Long? = null

    @Volatile var emit: ((Card) -> Unit)? = null

    @Volatile var pin: Pair<LocalDate, String?>? = null

    val pending get() = hold != null

    private fun asset(n: String) = c.assets.open(n).use { it.readBytes().decodeToString() }

    fun plates(max: Int?, veg: Boolean) = Plates.of(db, max, veg)

    fun chain(merchant: String?) = merchant?.let { menus.match(it) }?.first

    suspend fun resolver() = Resolver(repo.life.skus(), db, menus)

    suspend fun times(now: Long = System.currentTimeMillis()): Map<Meal, LocalTime> =
        MealTimes.learn(repo.life.samples(now), now, zone).mapKeys { Meal.valueOf(it.key.name) }

    suspend fun wen(text: String, now: Long = System.currentTimeMillis()): Wen =
        When.resolve(text, now, zone, times(now)) ?: When.resolve("", now, zone, times(now))!!

    suspend fun wenFor(day: LocalDate, slot: String?): Wen {
        if (day == LocalDate.now(zone)) return wen(slot.orEmpty())
        val m = Meal.entries.firstOrNull { it.label == slot } ?: Meal.of(LocalTime.now(zone))
        return Wen(day, m, day.atTime(times()[m] ?: LocalTime.NOON).atZone(zone).toInstant().toEpochMilli())
    }

    suspend fun target(): Int? {
        val p = repo.profileNow()
        p.kcalGoal?.let { return it }
        val kg = repo.life.weights(1).firstOrNull()?.kg ?: return null
        return Targets.kcal(kg, p.heightCm ?: return null, p.age ?: return null, p.sex, p.activity)
    }

    suspend fun log(reqs: List<Req>, w: Wen, src: String, note: String?, order: Long?): ToolOut =
        go(resolver(), pin?.let { wenFor(it.first, it.second) } ?: w, src, note, order, emptyList(), reqs)

    private suspend fun go(r: Resolver, w: Wen, src: String, note: String?, order: Long?, done: List<Hit>, todo: List<Req>): ToolOut {
        val got = done.toMutableList()
        val q = ArrayDeque(todo)
        while (q.isNotEmpty()) {
            val req = q.removeFirst()
            when (val x = r.resolve(req)) {
                is Hit -> got += x
                is Ask -> {
                    hold = Hold(w, src, note, order, got.toList(), x, q.toList())
                    return ToolOut.Ask(x.question)
                }
            }
        }
        hold = null
        return ToolOut.Ok(save(w, src, note, order, got))
    }

    suspend fun quiet(reqs: List<Req>, w: Wen, src: String, note: String?, order: Long?): String {
        val r = resolver()
        val hits = reqs.map { q -> when (val x = r.resolve(q)) { is Hit -> x; is Ask -> r.answer(q, r.guess(q).toDouble(), estimated = true) } }
        return save(w, src, note, order, hits)
    }

    suspend fun answer(text: String): ToolOut? {
        val h = hold ?: return null
        hold = null
        val r = resolver()
        val hit = when (val a = Clarify.parse(text, h.ask.size) ?: return null) {
            is Answer.Size -> return go(r, h.w, h.src, h.note, h.order, h.hits, listOf(h.ask.req.copy(size = a.s)) + h.rest)
            is Answer.Kcal -> {
                repo.life.saveSku(r.asked(h.ask.req, a.v))
                r.answer(h.ask.req, a.v)
            }
            Answer.Estimate -> r.answer(h.ask.req, r.guess(h.ask.req).toDouble(), estimated = true)
        }
        return go(r, h.w, h.src, h.note, h.order, h.hits + hit, h.rest)
    }

    fun cancel() {
        hold = null
    }

    private suspend fun save(w: Wen, src: String, note: String?, order: Long?, hits: List<Hit>): String {
        val id = if (order != null) order.also { repo.life.fillMeal(it, hits) } else repo.life.addMeal(w, src, note, hits)
        val row = repo.life.meal(id)
        val day = row?.let { LocalDate.ofEpochDay(it.day) } ?: w.day
        val slot = row?.slot ?: w.meal.label
        repo.life.eaten(day).firstOrNull { it.meal.id == id }?.let { Health.meal(c, it.meal, it.items) }
        Nudges.schedule(c)
        val total = hits.sumOf { it.kcal }
        val goal = target()
        val dayKcal = repo.life.kcalOn(day)
        emit?.invoke(Card.Meal(id, day.toEpochDay(), slot, Math.round(total).toInt(), hits.map { MealLine(it.name, "${qty(it.qty)} ${it.unit}", Math.round(it.kcal).toInt(), it.source.label == "estimate") }, Math.round(dayKcal).toInt(), goal))
        val items = hits.joinToString(", ") { "${it.name} ${qty(it.qty)}${it.unit.takeIf { u -> u != "serving" }?.let { u -> " $u" }.orEmpty()} ${k(it.kcal)}${if (it.source.label == "estimate") " est" else ""}" }
        return "Logged $slot on $day: $items. Meal ${k(total)} kcal. Day ${k(dayKcal)}${goal?.let { " of $it" }.orEmpty()} kcal."
    }

    suspend fun day(day: LocalDate): String {
        val ms = repo.life.eaten(day)
        if (ms.isEmpty()) return "Nothing logged for $day."
        val goal = target()
        emit?.invoke(Card.Day(day.toEpochDay(), Math.round(ms.sumOf(Eaten::kcal)).toInt(), goal, ms.filter { it.items.isNotEmpty() }.map { Bar(it.meal.slot, Math.round(it.kcal)) }))
        val lines = ms.joinToString("; ") { e -> "${e.meal.slot} ${if (e.items.isEmpty()) "pending order" else "${k(e.kcal)} kcal (${e.items.joinToString(", ") { it.name }})"}" }
        val burn = Health.burn(c, day)?.let { " Steps ${it.steps}, active ${k(it.kcal)} kcal." }.orEmpty()
        return "$day: $lines. Total ${k(ms.sumOf(Eaten::kcal))}${goal?.let { " of $it" }.orEmpty()} kcal.$burn"
    }

    private fun qty(q: Double) = if (q == q.toLong().toDouble()) q.toLong().toString() else String.format(Locale.US, "%.1f", q)

    private fun k(v: Double) = Math.round(v).toString()
}
