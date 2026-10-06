package app.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.chat.LogReq
import app.companion.chat.SetKcal
import app.companion.core.Req
import app.companion.core.Meal
import app.companion.core.Origin
import app.companion.core.Spark as Series
import app.companion.core.Targets
import app.companion.core.ToolOut
import app.companion.core.WeightNudge
import app.companion.data.Eaten
import app.companion.data.MealItemRow
import app.companion.data.Profile
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.clock
import app.companion.ui.dayLabel
import app.companion.ui.kit.Btn
import app.companion.ui.kit.CardShape
import app.companion.ui.kit.Chip
import app.companion.ui.kit.DayStrip
import app.companion.ui.kit.Empty
import app.companion.ui.kit.Field
import app.companion.ui.kit.Ic
import app.companion.ui.kit.LocalSnack
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Ring
import app.companion.ui.kit.Screen
import app.companion.ui.kit.Segmented
import app.companion.ui.kit.Spark
import app.companion.ui.kit.Tag
import app.companion.ui.kit.TextBtn
import app.companion.ui.kit.ToolButton
import app.companion.ui.kit.Tone
import app.companion.ui.kit.part
import app.companion.ui.num
import app.companion.ui.pal
import app.companion.ui.today
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private fun Double.n() = if (this == toLong().toDouble()) toLong().toString() else String.format(Locale.US, "%.1f", this)

private sealed interface Ln {
    class Head(val e: Eaten) : Ln
    class Item(val x: MealItemRow) : Ln
    class Order(val e: Eaten) : Ln
    class Add(val slot: Meal) : Ln
}

private fun rowsOf(meals: List<Eaten>, add: Boolean): List<Ln> = buildList {
    Meal.entries.forEach { slot ->
        val here = meals.filter { it.meal.slot == slot.label }
        if (here.isEmpty()) {
            if (add) add(Ln.Add(slot))
        } else {
            here.forEach { e ->
                if (e.items.isEmpty()) add(Ln.Order(e)) else {
                    add(Ln.Head(e))
                    e.items.forEach { add(Ln.Item(it)) }
                }
            }
        }
    }
}

@Composable
fun FoodScreen(go: (String) -> Unit) {
    val p = pal
    val c = LocalContext.current
    val repo = c.sl.repo
    val session = c.sl.session
    val ask = LocalAsk.current
    val now = today()
    var sel by rememberSaveable { mutableStateOf<Long?>(null) }
    val day = sel?.let(LocalDate::ofEpochDay) ?: now
    val meals by remember(day) { repo.life.eatenOn(day) }.collectAsStateWithLifecycle(emptyList())
    val prof by repo.profile.collectAsStateWithLifecycle(Profile())
    val weights by remember { repo.life.weightFlow(60) }.collectAsStateWithLifecycle(emptyList())
    val goal = rememberGoal()
    var edit by remember { mutableStateOf<MealItemRow?>(null) }
    var setGoal by remember { mutableStateOf(false) }
    var weigh by remember { mutableStateOf(false) }
    val log = { slot: String?, order: Long? -> ask(AskReq(null, "I had ", log = LogReq(day, slot, order))) }
    val rows = remember(meals, day) { rowsOf(meals, day <= now) }
    val kcal = meals.sumOf { it.kcal }.roundToInt()
    val kg = weights.firstOrNull()?.kg
    val macros = remember(goal, kg, prof.proteinGoal) { goal?.let { Targets.macros(it, kg, prof.proteinGoal) } }
    val eaten = remember(meals) { Triple(meals.sumOf { e -> e.items.sumOf { it.protein ?: 0.0 } }, meals.sumOf { e -> e.items.sumOf { it.carbs ?: 0.0 } }, meals.sumOf { e -> e.items.sumOf { it.fat ?: 0.0 } }) }
    val ask1 = session.pending
    Screen(
        "Food",
        listOfNotNull(if (day == now) "Today" else dayLabel(day), "${num(kcal)} kcal${goal?.let { " of ${num(it)}" }.orEmpty()}").joinToString(" · "),
        tall = false,
        tools = { ToolButton(Ic.Add, "Log a meal") { log(null, null) } },
        lead = {
            Column {
                DayStrip(day, now, Modifier.padding(top = 4.dp)) { sel = it.takeIf { x -> x != now }?.toEpochDay() }
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth().clip(CardShape).background(p.card).padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ring(if (goal != null) kcal.toFloat() / goal else 0f, 104.dp, 10.dp) {
                            Text(num(if (goal != null) abs(goal - kcal) else kcal), style = Ty.mono(22, FontWeight.Bold).copy(color = p.ink), maxLines = 1)
                        }
                        Column(Modifier.padding(start = 20.dp).weight(1f)) {
                            Text(if (goal == null) "kcal eaten" else if (goal >= kcal) "kcal left" else "kcal over", style = Ty.ui(16, FontWeight.Medium).copy(color = p.ink))
                            if (goal != null) Text("Goal ${num(goal)}", Modifier.padding(top = 2.dp), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2))
                        }
                    }
                    Row(Modifier.padding(top = 18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Macro("Protein", eaten.first, macros?.protein, Modifier.weight(1f))
                        Macro("Carbs", eaten.second, macros?.carbs, Modifier.weight(1f))
                        Macro("Fat", eaten.third, macros?.fat, Modifier.weight(1f))
                    }
                }
                if (goal == null) {
                    Box(Modifier.padding(top = 12.dp).part(p, true, true)) {
                        PassLine("Set a goal", "Work it out from your weight, height and activity", lead = Ic.Plan, onClick = { setGoal = true }, trailing = { Btn("Set", dense = true, go = true) { setGoal = true } })
                    }
                }
            }
        },
    ) {
        if (ask1 != null) {
            item(key = "pending") { Box(Modifier.padding(top = 12.dp)) { PickCard(ask1, "Waiting on you") { o -> o.send?.let { session.send(it) } ?: ask(AskReq(null)) } } }
        }
        item(key = "gap") { Box(Modifier.padding(top = 8.dp)) }
        if (rows.isEmpty()) {
            item(key = "none") { Empty(Ic.Food, "Nothing logged", "Meals you log for ${dayLabel(day)} show here.") }
        }
        itemsIndexed(rows, key = { _, r -> when (r) { is Ln.Head -> "h${r.e.meal.id}"; is Ln.Item -> "i${r.x.id}"; is Ln.Order -> "o${r.e.meal.id}"; is Ln.Add -> "a${r.slot.name}" } }) { k, r ->
            Box(Modifier.animateItem().part(p, k == 0, k == rows.lastIndex)) {
                when (r) {
                    is Ln.Head -> MealHead(r.e) { log(r.e.meal.slot, null) }
                    is Ln.Item -> ItemLine(r.x) { edit = r.x }
                    is Ln.Order -> PassLine(
                        "${r.e.meal.slot.cap()} order", r.e.meal.note.orEmpty().ifBlank { "Not logged" }, lead = Ic.Food, tone = Tone.Plain, lines = 1,
                        trailing = { Btn("Log", dense = true, go = true) { log(r.e.meal.slot, r.e.meal.id) } },
                    )
                    is Ln.Add -> Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClickLabel = "Add ${r.slot.label}") { log(r.slot.label, null) }.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Ic.Add, null, Modifier.size(20.dp), tint = p.ink2)
                        Text("Add ${r.slot.label}", Modifier.padding(start = 12.dp), style = Ty.ui(15, FontWeight.Medium).copy(color = p.ink2))
                    }
                }
            }
        }
        item(key = "weight") { WeightCard(weights.map { LocalDate.ofEpochDay(it.day) to it.kg }, now) { weigh = true } }
    }
    edit?.let { ItemSheet(it) { edit = null } }
    if (setGoal) GoalSheet(prof, kg) { setGoal = false }
    if (weigh) WeightSheet { weigh = false }
}

@Composable
private fun Macro(label: String, v: Double, goal: Int?, modifier: Modifier) {
    val p = pal
    Column(modifier) {
        Text(label, style = Ty.ui(13, FontWeight.Medium).copy(color = p.ink2), maxLines = 1)
        Text(if (goal != null) "${v.roundToInt()} / $goal g" else "${v.roundToInt()} g", Modifier.padding(top = 2.dp), style = Ty.mono(13, FontWeight.SemiBold).copy(color = p.ink), maxLines = 1)
        Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.line)) {
            Box(Modifier.fillMaxWidth(if (goal != null && goal > 0) (v / goal).toFloat().coerceIn(0f, 1f) else 0f).fillMaxHeight().background(p.accent))
        }
    }
}

@Composable
private fun MealHead(e: Eaten, add: () -> Unit) {
    val p = pal
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(e.meal.slot.cap(), style = Ty.ui(15, FontWeight.SemiBold).copy(color = p.ink), maxLines = 1)
        Text(clock(e.meal.at), Modifier.padding(start = 10.dp), style = Ty.mono(13, FontWeight.Normal).copy(color = p.ink2), maxLines = 1)
        Text("${num(e.kcal.roundToInt())} kcal", Modifier.weight(1f).padding(end = 6.dp), style = Ty.mono(14, FontWeight.Medium).copy(color = p.ink2), maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        TextBtn("Add", color = p.accent, onClick = add)
    }
}

@Composable
private fun ItemLine(x: MealItemRow, onClick: () -> Unit) {
    val p = pal
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button, onClickLabel = "Edit ${x.name}", onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(x.name.cap(), style = Ty.ui(16, FontWeight.Medium).copy(color = p.ink), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(listOfNotNull("${x.qty.n()} ${x.unit.takeIf { it != "serving" } ?: "serving"}", Req.brand(x.brand)).joinToString(" · "), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2), maxLines = 1)
                Origin.of(x.source)?.let { Tag(it.badge) }
            }
        }
        Text("${num(x.kcal.roundToInt())} kcal", Modifier.padding(start = 12.dp), style = Ty.mono(16, FontWeight.SemiBold).copy(color = p.ink), maxLines = 1)
    }
}

@Composable
private fun WeightCard(points: List<Pair<LocalDate, Double>>, now: LocalDate, log: () -> Unit) {
    val p = pal
    val pts = remember(points, now) { Series.window(points, now, 28) }
    val trend = remember(points) { WeightNudge.trend(points) }
    val last = points.maxByOrNull { it.first }
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).fillMaxWidth().clip(CardShape).background(p.card).padding(start = 20.dp, top = 16.dp, bottom = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Weight", style = Ty.ui(13, FontWeight.Medium).copy(color = p.ink2))
            Text(last?.let { String.format(Locale.US, "%.1f kg", it.second) } ?: "Not logged", Modifier.padding(top = 2.dp), style = if (last != null) Ty.mono(26, FontWeight.Bold).copy(color = p.ink) else Ty.ui(16, FontWeight.Medium).copy(color = p.ink), maxLines = 1)
            Text(
                trend?.let { String.format(Locale.US, "%+.1f kg a week", it.slopePerWeek) } ?: "Log once a week to see a trend",
                Modifier.padding(top = 2.dp), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2), maxLines = 1,
            )
        }
        if (pts.size >= 2) Spark(pts.map { it.second }, Modifier.size(84.dp, 40.dp), "Weight over four weeks")
        Btn("Log", Modifier.padding(start = 8.dp), dense = true) { log() }
    }
}

@Composable
private fun ItemSheet(x: MealItemRow, onClose: () -> Unit) {
    val p = pal
    val repo = LocalContext.current.sl.repo
    val snack = LocalSnack.current
    var qty by remember { mutableStateOf(x.qty.n()) }
    var kcal by remember { mutableStateOf(x.kcal.roundToInt().toString()) }
    var typed by remember { mutableStateOf(false) }
    val q = qty.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    val k = kcal.toDoubleOrNull()?.takeIf { it in 1.0..5000.0 }
    val ok = q != null && k != null
    val save = {
        val qv = q ?: 1.0
        val kv = k ?: 0.0
        onClose()
        snack.go {
            val f = qv / x.qty
            repo.life.editItem(x.copy(qty = qv, kcal = kv, protein = x.protein?.times(f), carbs = x.carbs?.times(f), fat = x.fat?.times(f), source = Origin.Sku.label, conf = 1.0))
            val r = SetKcal(repo).run(
                buildMap {
                    put("item_key", x.brand?.let { "$it|${x.name}" } ?: x.name)
                    put("kcal", kv / qv)
                    x.protein?.let { put("protein", it / x.qty) }
                    x.carbs?.let { put("carbs", it / x.qty) }
                    x.fat?.let { put("fat", it / x.qty) }
                },
            )
            snack.say(if (r is ToolOut.Ok) "Saved ${x.name} for next time" else "Couldn't save that")
        }
    }
    Sheet(onClose) {
        SheetTitle(x.name.cap(), Origin.of(x.source)?.badge?.cap())
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(
                "Portion in ${x.unit.takeIf { it != "serving" }?.let { u -> "${u}s" } ?: "servings"}", qty,
                { v ->
                    qty = v.take(6)
                    val a = v.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
                    if (a != null && !typed) kcal = (x.kcal / x.qty * a).roundToInt().toString()
                },
                Modifier.weight(1f), keyboard = KeyboardType.Decimal, mono = true,
            )
            Field("Calories", kcal, { typed = true; kcal = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), keyboard = KeyboardType.Number, mono = true, ime = ImeAction.Done) { if (ok) save() }
        }
        Text("Saved as your own food, so next time it is not asked again.", Modifier.padding(top = 10.dp), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2))
        Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Btn("Save", go = true, enabled = ok) { save() }
            TextBtn("Remove", color = p.red) {
                onClose()
                snack.go { repo.life.dropItem(x); snack.say("Removed ${x.name}") }
            }
        }
    }
}

private val levels = listOf("sedentary" to "Mostly sitting", "light" to "Light", "moderate" to "Moderate", "active" to "Active")

@Composable
private fun GoalSheet(prof: Profile, kg0: Double?, onClose: () -> Unit) {
    val p = pal
    val repo = LocalContext.current.sl.repo
    val snack = LocalSnack.current
    var manual by remember { mutableStateOf(0) }
    var kg by remember { mutableStateOf(kg0?.n().orEmpty()) }
    var cm by remember { mutableStateOf(prof.heightCm?.toString().orEmpty()) }
    var age by remember { mutableStateOf(prof.age?.toString().orEmpty()) }
    var sex by remember { mutableStateOf(if (prof.sex?.lowercase()?.startsWith("f") == true) "female" else "male") }
    var act by remember { mutableStateOf(prof.activity ?: "light") }
    var kcal by remember { mutableStateOf(prof.kcalGoal?.toString().orEmpty()) }
    val w = kg.replace(',', '.').toDoubleOrNull()
    val calc = if (w != null) Targets.kcal(w, cm.toIntOrNull() ?: 0, age.toIntOrNull() ?: 0, sex, act) else null
    val value = if (manual == 0) calc else kcal.toIntOrNull()?.takeIf { it in 800..6000 }
    val save = {
        val v = value ?: 0
        val h = cm.toIntOrNull()
        val a = age.toIntOrNull()
        onClose()
        snack.go {
            val now = System.currentTimeMillis()
            if (manual == 0 && w != null && kg0 == null) repo.life.weigh(today(), w, now)
            repo.edit { if (manual == 0) it.copy(kcalGoal = v, heightCm = h, age = a, sex = sex, activity = act) else it.copy(kcalGoal = v) }
            snack.say("Goal set to ${num(v)} kcal")
        }
    }
    Sheet(onClose) {
        SheetTitle("Daily calorie goal", "Stays on this phone")
        Segmented(listOf("Work it out", "Enter it"), manual, Modifier.padding(top = 16.dp)) { manual = it }
        if (manual == 0) {
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Weight kg", kg, { kg = it.take(6) }, Modifier.weight(1f), keyboard = KeyboardType.Decimal, mono = true)
                Field("Height cm", cm, { cm = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), keyboard = KeyboardType.Number, mono = true)
                Field("Age", age, { age = it.filter(Char::isDigit).take(3) }, Modifier.weight(0.8f), keyboard = KeyboardType.Number, mono = true)
            }
            FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("male" to "Male", "female" to "Female").forEach { (k, l) -> Chip(l, sex == k) { sex = k } }
            }
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                levels.forEach { (k, l) -> Chip(l, act == k) { act = k } }
            }
        } else {
            Field("Calories a day", kcal, { kcal = it.filter(Char::isDigit).take(4) }, Modifier.padding(top = 14.dp), keyboard = KeyboardType.Number, mono = true)
        }
        Text(value?.let { "${num(it)} kcal a day" } ?: "Fill in the details", Modifier.padding(top = 16.dp), style = Ty.mono(24, FontWeight.Bold).copy(color = if (value != null) p.ink else p.ink2))
        Btn("Save", Modifier.padding(top = 16.dp), go = true, enabled = value != null) { save() }
    }
}
