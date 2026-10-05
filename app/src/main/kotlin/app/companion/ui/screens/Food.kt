package app.companion.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.core.Meal
import app.companion.data.MealItemRow
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.dayLabel
import app.companion.ui.kit.Btn
import app.companion.ui.kit.CardShape
import app.companion.ui.kit.DayStrip
import app.companion.ui.kit.Ic
import app.companion.ui.kit.Ring
import app.companion.ui.kit.Screen
import app.companion.ui.kit.Section
import app.companion.ui.kit.ToolButton
import app.companion.ui.kit.Tone
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.part
import app.companion.ui.num
import app.companion.ui.pal
import app.companion.ui.today
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

private fun MealItemRow.line() = listOfNotNull(
    "${if (qty == qty.toLong().toDouble()) qty.toLong().toString() else "%.1f".format(qty)} ${unit.takeIf { it != "serving" } ?: "serving"}",
    brand,
).joinToString(" · ")

@Composable
fun FoodScreen(go: (String) -> Unit) {
    val p = pal
    val repo = LocalContext.current.sl.repo
    val now = today()
    var sel by rememberSaveable { mutableStateOf<Long?>(null) }
    val day = sel?.let(LocalDate::ofEpochDay) ?: now
    val meals by remember(day) { repo.life.eatenOn(day) }.collectAsStateWithLifecycle(emptyList())
    val goal = rememberGoal()
    var logging by remember { mutableStateOf<LogReq?>(null) }
    val kcal = meals.sumOf { it.kcal }.roundToInt()
    val macros = listOf("Protein" to meals.sumOf { e -> e.items.sumOf { it.protein ?: 0.0 } }, "Carbs" to meals.sumOf { e -> e.items.sumOf { it.carbs ?: 0.0 } }, "Fat" to meals.sumOf { e -> e.items.sumOf { it.fat ?: 0.0 } })
        .filter { it.second > 0 }.joinToString(" · ") { "${it.first} ${it.second.roundToInt()} g" }
    Screen(
        "Food",
        listOfNotNull(if (day == now) "Today" else dayLabel(day), "${num(kcal)} kcal${goal?.let { " of ${num(it)}" }.orEmpty()}").joinToString(" · "),
        tall = false,
        tools = { ToolButton(Ic.Add, "Log a meal") { logging = LogReq(day) } },
        lead = {
            Column {
                DayStrip(day, now, Modifier.padding(top = 4.dp)) { sel = it.takeIf { x -> x != now }?.toEpochDay() }
                Row(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth().clip(CardShape).background(p.card).padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ring(if (goal != null) kcal.toFloat() / goal else 0f, 104.dp, 10.dp) {
                        Text(num(if (goal != null) abs(goal - kcal) else kcal), style = Ty.mono(22, FontWeight.Bold).copy(color = p.ink), maxLines = 1)
                    }
                    Column(Modifier.padding(start = 20.dp).weight(1f)) {
                        Text(if (goal == null) "kcal eaten" else if (goal >= kcal) "kcal left" else "kcal over", style = Ty.ui(16, FontWeight.Medium).copy(color = p.ink))
                        if (goal != null) Text("Goal ${num(goal)}", Modifier.padding(top = 2.dp), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2))
                        if (macros.isNotEmpty()) Text(macros, Modifier.padding(top = 8.dp), style = Ty.mono(13, FontWeight.Normal).copy(color = p.ink2))
                    }
                }
            }
        },
    ) {
        Meal.entries.forEach { slot ->
            val rows = meals.filter { it.meal.slot == slot.label }
            item(key = "h${slot.name}") { Section(slot.label.cap() + if (rows.isNotEmpty()) " · ${num(rows.sumOf { it.kcal }.roundToInt())} kcal" else "", if (day <= now) "Add" else null) { logging = LogReq(day, slot.label) } }
            if (rows.isEmpty()) {
                item(key = "n${slot.name}") { Box(Modifier.part(p, true, true)) { PassLine("Nothing logged", "", lead = Ic.Food, tone = Tone.Plain, onClick = { logging = LogReq(day, slot.label) }) } }
            }
            rows.forEachIndexed { r, e ->
                if (e.items.isEmpty()) {
                    item(key = "m${e.meal.id}") {
                        Box(Modifier.part(p, r == 0, r == rows.lastIndex)) {
                            PassLine("Order not logged", e.meal.note.orEmpty(), lead = Ic.Food, tone = Tone.Plain, trailing = { Btn("Log", dense = true, go = true) { logging = LogReq(day, slot.label, e.meal.id) } })
                        }
                    }
                }
                e.items.forEachIndexed { k, x ->
                    item(key = "i${x.id}") {
                        Box(Modifier.part(p, r == 0 && k == 0, r == rows.lastIndex && k == e.items.lastIndex)) {
                            PassLine(x.name, x.line(), lead = Ic.Food, tone = Tone.Plain, lines = 1, trailing = { Text("${num(x.kcal.roundToInt())} kcal", style = Ty.mono(16, FontWeight.SemiBold).copy(color = p.ink), maxLines = 1) })
                        }
                    }
                }
            }
        }
    }
    logging?.let { LogSheet(it) { logging = null } }
}
