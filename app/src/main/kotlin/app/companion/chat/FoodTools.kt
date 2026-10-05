package app.companion.chat

import android.content.Context
import app.companion.core.Card
import app.companion.core.Req
import app.companion.core.Sku
import app.companion.core.ToolSpecs
import app.companion.core.ToolOut
import app.companion.core.WeightNudge
import app.companion.data.Repo
import app.companion.system.Health
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private val zone get() = ZoneId.systemDefault()

class LogMeal(private val foods: Foods) : Base(ToolSpecs.logMeal) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val reqs = a.l("items").mapNotNull { it.m() }.mapNotNull { i ->
            val n = i.s("name") ?: return@mapNotNull null
            Req(n, i.s("brand"), (i.d("qty") ?: 1.0).takeIf { it > 0 } ?: 1.0, i.s("unit"), i.s("size"), i.l("mods").mapNotNull { it as? String })
        }
        if (reqs.isEmpty()) return ToolOut.Fail("items")
        val order = foods.order
        val w = foods.wen(listOfNotNull(a.s("when"), a.s("meal")).joinToString(" "))
        return foods.log(reqs, w, if (order != null) "order" else "chat", null, order)
    }
}

class FoodToday(private val foods: Foods) : Base(ToolSpecs.foodToday) {
    override suspend fun run(a: Map<String, Any?>): ToolOut = ToolOut.Ok(foods.day(foods.wen(a.s("date").orEmpty()).day))
}

class SuggestMeal(private val foods: Foods) : Base(ToolSpecs.suggestMeal) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val cap = a.d("kcal")?.toInt()?.takeIf { it in 100..3000 }
        val veg = a["veg"] == true
        val p = foods.plates(cap, veg)
        if (p.isEmpty()) return ToolOut.Ok("Nothing in my food list fits under ${cap ?: 700} kcal. Try a higher limit.")
        val head = listOfNotNull("under ${cap ?: 700} kcal", "vegetarian".takeIf { veg }, "most protein first").joinToString(", ")
        val goal = foods.target()?.let { "\nYour goal is $it kcal a day." }.orEmpty()
        return ToolOut.Ok("Ideas $head:\n" + p.joinToString("\n") { "• " + it.line() } + goal + "\nSay what you pick and I'll log it.")
    }
}

class SetKcal(private val repo: Repo) : Base(ToolSpecs.setKcal) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val key = a.s("item_key") ?: return ToolOut.Fail("item_key")
        val kcal = a.d("kcal")?.takeIf { it in 1.0..5000.0 } ?: return ToolOut.Fail("kcal")
        val (brand, n) = key.split('|', limit = 2).let { if (it.size == 2) it[0].trim().ifEmpty { null } to it[1].trim() else null to it[0].trim() }
        repo.life.saveSku(Sku(Sku.key(brand, n), brand, n, kcal, a.d("protein"), a.d("carbs"), a.d("fat")))
        return ToolOut.Ok("Saved $n at ${Math.round(kcal)} kcal per serving.")
    }
}

class LogWeight(private val c: Context, private val repo: Repo, private val show: (Card) -> Unit = {}) : Base(ToolSpecs.logWeight) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val kg = a.d("kg")?.takeIf { it in 25.0..300.0 } ?: return ToolOut.Fail("kg")
        val now = System.currentTimeMillis()
        val day = a.s("when")?.let { app.companion.core.When.resolve(it, now, zone)?.day } ?: LocalDate.now(zone)
        repo.life.weigh(day, kg, now)
        Health.weight(c, kg, now)
        val t = WeightNudge.trend(repo.life.weights(60).map { LocalDate.ofEpochDay(it.day) to it.kg })
        show(Card.Weight(kg, day.toEpochDay(), t?.slopePerWeek, t?.avg7))
        val tail = t?.let { String.format(Locale.US, " Trend %+.1f kg a week, 7 day average %.1f.", it.slopePerWeek, it.avg7) }.orEmpty()
        return ToolOut.Ok("Saved ${kg} kg for $day.$tail")
    }
}
