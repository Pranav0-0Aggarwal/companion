package app.companion.data

import androidx.room.withTransaction
import app.companion.core.Eat
import app.companion.core.Forex
import app.companion.core.Hit
import app.companion.core.Meal
import app.companion.core.Sku
import app.companion.core.TItem
import app.companion.core.TSpan
import app.companion.core.TSpend
import app.companion.core.TravelHint
import app.companion.core.Trips
import app.companion.core.TripSummary
import app.companion.core.Wen
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class Eaten(val meal: MealRow, val items: List<MealItemRow>) {
    val kcal get() = items.sumOf { it.kcal }
}

class Lives(private val db: Db) {
    private val d = db.life()
    private val zone get() = ZoneId.systemDefault()

    suspend fun skus(): Map<String, Sku> = d.skus().associate { it.key to Sku(it.key, it.brand, it.name, it.kcal, it.protein, it.carbs, it.fat, it.per) }

    suspend fun saveSku(s: Sku, now: Long = System.currentTimeMillis()) = d.putSku(SkuRow(s.key, s.brand, s.name, s.kcal, s.protein, s.carbs, s.fat, s.per, now))

    private fun rows(id: Long, hits: List<Hit>) = hits.map { MealItemRow(mealId = id, name = it.name, brand = it.brand, qty = it.qty, unit = it.unit, kcal = it.kcal, protein = it.protein, carbs = it.carbs, fat = it.fat, source = it.source.label, conf = it.conf) }

    suspend fun addMeal(w: Wen, src: String, note: String?, hits: List<Hit>): Long = db.withTransaction {
        val id = d.addMeal(MealRow(day = w.day.toEpochDay(), slot = w.meal.label, at = w.at, src = src, note = note))
        d.addMealItems(rows(id, hits))
        id
    }

    suspend fun fillMeal(id: Long, hits: List<Hit>) = db.withTransaction {
        d.clearMeal(id)
        d.addMealItems(rows(id, hits))
    }

    suspend fun addBare(w: Wen, src: String, note: String): Long = d.addMeal(MealRow(day = w.day.toEpochDay(), slot = w.meal.label, at = w.at, src = src, note = note))

    suspend fun meal(id: Long) = d.meal(id)

    suspend fun orderAt(at: Long) = d.orderAt(at)

    suspend fun bareOrders() = d.bareOrders()

    suspend fun dropMeal(id: Long) = d.dropMeal(id)

    suspend fun eaten(day: LocalDate): List<Eaten> {
        val ms = d.mealsOn(day.toEpochDay())
        if (ms.isEmpty()) return emptyList()
        val by = d.mealItems(ms.map { it.id }).groupBy { it.mealId }
        return ms.map { Eaten(it, by[it.id].orEmpty()) }
    }

    fun eatenOn(day: LocalDate): Flow<List<Eaten>> = combine(d.mealsBetween(day.toEpochDay(), day.toEpochDay()), d.itemsBetween(day.toEpochDay(), day.toEpochDay())) { ms, its ->
        val by = its.groupBy { it.mealId }
        ms.map { Eaten(it, by[it.id].orEmpty()) }
    }

    suspend fun scale(id: Long, f: Double) = db.withTransaction {
        val rows = d.mealItems(listOf(id))
        d.clearMeal(id)
        d.addMealItems(rows.map { it.copy(id = 0, qty = it.qty * f, kcal = it.kcal * f, protein = it.protein?.times(f), carbs = it.carbs?.times(f), fat = it.fat?.times(f)) })
    }

    suspend fun editItem(i: MealItemRow) = d.updateItem(i)

    suspend fun dropItem(i: MealItemRow) = db.withTransaction {
        d.dropItem(i.id)
        if (d.itemCount(i.mealId) == 0 && d.meal(i.mealId)?.src != "order") d.dropMeal(i.mealId)
    }

    suspend fun kcalOn(day: LocalDate) = eaten(day).sumOf { it.kcal }

    suspend fun foodPaid(lo: Long, hi: Long) = d.foodPaid(lo, hi)

    suspend fun samples(now: Long): List<Pair<Eat, Long>> {
        val since = now - 21 * 86_400_000L
        val logged = d.mealTimes(since).mapNotNull { r -> Meal.entries.firstOrNull { it.label == r.slot }?.let { Eat.valueOf(it.name) to r.at } }
        val paid = d.foodPaid(since, now).map { Eat.valueOf(Meal.of(Instant.ofEpochMilli(it).atZone(zone).toLocalTime()).name) to it }
        return logged + paid
    }

    suspend fun weigh(day: LocalDate, kg: Double, at: Long) = d.putWeight(WeightRow(day.toEpochDay(), kg, at))

    suspend fun weights(n: Int) = d.weights(n)

    fun weightFlow(n: Int) = d.weightFlow(n)

    suspend fun startTrip(name: String, start: Long, end: Long, budget: Long? = null, currency: String? = null): Long = db.withTransaction {
        d.deactivate()
        val id = d.addTrip(TripRow(name = name, start = start, end = end, budget = budget, currency = currency, active = true))
        d.untagged(start, end).forEach { d.tag(TripItemRow(id, it.id, 1.0, null)) }
        id
    }

    suspend fun endTrip(now: Long): TripRow? = db.withTransaction {
        val t = d.activeTrip() ?: return@withTransaction null
        t.copy(end = maxOf(now, t.start), active = false).also { d.updateTrip(it) }
    }

    suspend fun activeTrip() = d.activeTrip()

    suspend fun tripNamed(name: String) = d.tripNamed(name)

    suspend fun trips() = d.tripsNow()

    fun tripFlow() = d.trips()

    suspend fun dropTrip(id: Long) = d.dropTrip(id)

    suspend fun tagLive(item: Item, text: String) {
        val spans = d.tripsNow().map { it.id to TSpan(it.start, if (it.active) Long.MAX_VALUE else it.end) }
        val trip = Trips.tag(item.at, spans) ?: return
        val inr = if (item.currency == "INR") null else Forex.parse(text)?.let { Forex.inr(item.paise, it) }
        d.tag(TripItemRow(trip, item.id, 1.0, inr))
    }

    suspend fun trip(id: Long) = d.trip(id)

    suspend fun spends(t: TripRow): List<TSpend> {
        val by = d.shares(t.id).associateBy { it.itemId }
        return d.tripItems(t.id).filter { it.kind != "Credit" && (it.currency == "INR" || by[it.id]?.inr != null) }.map { i ->
            val s = by[i.id]
            TSpend(i.id, i.at, i.title, i.category, s?.inr ?: i.paise, s?.share ?: 1.0)
        }
    }

    suspend fun untag(trip: Long, item: Long) = d.untag(trip, item)

    suspend fun setShare(trip: Long, item: Long, share: Double) {
        val cur = d.shares(trip).firstOrNull { it.itemId == item } ?: return
        d.tag(cur.copy(share = share.coerceIn(0.0, 1.0)))
    }

    suspend fun summary(t: TripRow) = run {
        val by = d.shares(t.id).associateBy { it.itemId }
        val items = d.tripItems(t.id).filter { it.kind != "Credit" && (it.currency == "INR" || by[it.id]?.inr != null) }.map { i ->
            val s = by[i.id]
            TItem(i.id, i.at, s?.inr ?: i.paise, i.category, i.merchant, s?.share ?: 1.0)
        }
        TripSummary.of(items, t.start, t.end, zone)
    }

    suspend fun suggestions(now: Long) = d.travel(now - 7 * 86_400_000L).map { i ->
        TravelHint(i.title.removeSuffix(" booking"), i.due?.let(LocalDate::ofEpochDay), i.title)
    }.let { hints ->
        val have = d.tripsNow()
        TripSummary.suggest(hints, now, zone).filter { p -> have.none { it.start <= p.end && p.start <= it.end } }
    }

    suspend fun queue(kind: String, ref: Long?, slot: String?, text: String, now: Long = System.currentTimeMillis()) = d.addReply(ReplyRow(at = now, kind = kind, ref = ref, slot = slot, text = text))

    suspend fun replies() = d.replies()

    suspend fun done(id: Long) = d.dropReply(id)
}
