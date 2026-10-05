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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.core.Bar
import app.companion.core.Bars
import app.companion.core.Pick
import app.companion.core.Split
import app.companion.core.Summary
import app.companion.core.TSpend
import app.companion.core.TripView
import app.companion.data.TripRow
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.dateOf
import app.companion.ui.dayLabel
import app.companion.ui.inr
import app.companion.ui.kit.Amount
import app.companion.ui.kit.Btn
import app.companion.ui.kit.CardShape
import app.companion.ui.kit.Chip
import app.companion.ui.kit.Empty
import app.companion.ui.kit.Field
import app.companion.ui.kit.Ic
import app.companion.ui.kit.Lead
import app.companion.ui.kit.LocalSnack
import app.companion.ui.kit.Screen
import app.companion.ui.kit.Stamp
import app.companion.ui.kit.Tag
import app.companion.ui.kit.TextBtn
import app.companion.ui.kit.ToolButton
import app.companion.ui.kit.Tone
import app.companion.ui.kit.empty
import app.companion.ui.kit.part
import app.companion.ui.pal
import app.companion.ui.span
import app.companion.ui.today
import app.companion.ui.zone
import java.time.LocalDate
import kotlinx.coroutines.launch

private fun startOf(d: LocalDate) = d.atStartOfDay(zone()).toInstant().toEpochMilli()

private fun endOf(d: LocalDate) = d.plusDays(1).atStartOfDay(zone()).toInstant().toEpochMilli() - 1

private fun TripRow.dates() = span(dateOf(start), dateOf(end))

@Composable
private fun CatBar(top: List<Bar>, modifier: Modifier = Modifier) {
    val p = pal
    val f = Bars.share(top)
    Row(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.line), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        top.forEachIndexed { i, _ -> if (f[i] > 0f) Box(Modifier.weight(f[i]).fillMaxHeight().background(p.accent.copy(alpha = 1f - i * 0.18f))) }
    }
}

@Composable
private fun CatLegend(top: List<Bar>, modifier: Modifier = Modifier) {
    val p = pal
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        top.forEachIndexed { i, b ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(p.accent.copy(alpha = 1f - i * 0.18f), CircleShape))
                Text(b.label.cap(), Modifier.weight(1f).padding(start = 10.dp), style = Ty.ui(14, FontWeight.Medium).copy(color = p.ink), maxLines = 1)
                Text(inr(b.value), style = Ty.mono(14, FontWeight.Medium).copy(color = p.ink2), maxLines = 1)
            }
        }
    }
}

@Composable
fun TripsScreen(go: (String) -> Unit, lead: @Composable () -> Unit) {
    val p = pal
    val repo = LocalContext.current.sl.repo
    val trips by remember { repo.life.tripFlow() }.collectAsStateWithLifecycle(emptyList())
    val money by repo.money.collectAsStateWithLifecycle(emptyList())
    val sums by produceState(emptyMap<Long, Summary>(), trips, money) { value = trips.associate { it.id to repo.life.summary(it) } }
    val picks by produceState(emptyList<Pick>(), trips) { value = repo.life.suggestions(System.currentTimeMillis()) }
    var sheet by remember { mutableStateOf(false) }
    var seed by remember { mutableStateOf<Pick?>(null) }
    var ending by remember { mutableStateOf<TripRow?>(null) }
    var skip by rememberSaveable { mutableStateOf(setOf<String>()) }
    val total = sums.values.sumOf { it.total }
    val shown = picks.filter { it.name + it.start !in skip }
    Screen(
        "Money", if (trips.isEmpty()) "No trips yet" else "${trips.size} ${if (trips.size == 1) "trip" else "trips"} · ${inr(total)}", lead = lead, tall = false,
        tools = { ToolButton(Ic.Add, "Start a trip") { seed = null; sheet = true } },
    ) {
        if (trips.isEmpty() && shown.isEmpty()) {
            empty(Ic.Trip, "No trips yet", "Start a trip and every spend in its dates is tagged to it.") {
                Btn("Start a trip", go = true, icon = Ic.Add) { seed = null; sheet = true }
            }
        }
        itemsIndexed(shown, key = { _, t -> "p${t.name}${t.start}" }) { _, t ->
            Column(Modifier.animateItem().padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth().clip(CardShape).background(p.accentBox).padding(18.dp)) {
                Text("${t.name} detected", style = Ty.ui(16, FontWeight.SemiBold).copy(color = p.onAccentBox))
                Text("${span(dateOf(t.start), dateOf(t.end))} · Start trip mode?", Modifier.padding(top = 2.dp), style = Ty.ui(14, FontWeight.Normal).copy(color = p.onAccentBox))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Btn("Start", go = true) { seed = t; sheet = true }
                    TextBtn("Not now", color = p.onAccentBox) { skip = skip + (t.name + t.start) }
                }
            }
        }
        itemsIndexed(trips, key = { _, t -> "t${t.id}" }) { _, t ->
            val s = sums[t.id]
            Column(
                Modifier.animateItem().padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth().clip(CardShape).background(p.card)
                    .clickable(role = Role.Button, onClickLabel = "Open ${t.name}") { go("trip/${t.id}") }.padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Lead(Ic.Trip, if (t.active) Tone.Accent else Tone.Plain)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(t.name, style = Ty.ui(18, FontWeight.Bold).copy(color = p.ink), maxLines = 1)
                        Text("${t.dates()}${s?.let { " · ${it.days} ${if (it.days == 1) "day" else "days"}" }.orEmpty()}", style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2), maxLines = 1)
                    }
                    if (t.active) Stamp("ACTIVE", ok = true)
                }
                if (s != null) {
                    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.Bottom) {
                        Amount(inr(s.total), Ty.mono(28, FontWeight.Bold).copy(color = p.ink), Modifier.weight(1f))
                        Text("${inr(s.perDay)} a day", Modifier.padding(start = 12.dp, bottom = 4.dp), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2), maxLines = 1)
                    }
                    val top = s.top.map { Bar(it.first, it.second) }
                    if (top.isNotEmpty()) {
                        CatBar(top, Modifier.padding(top = 12.dp))
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Tag("Top: ${top.first().label} ${inr(top.first().value)}") }
                    }
                }
                if (t.active) TextBtn("End trip", Modifier.padding(top = 4.dp)) { ending = t }
            }
        }
    }
    if (sheet) StartTrip(seed) { sheet = false }
    ending?.let { EndTrip(it, sums[it.id]) { ending = null } }
}

@Composable
private fun StartTrip(seed: Pick?, onClose: () -> Unit) {
    val repo = LocalContext.current.sl.repo
    val snack = LocalSnack.current
    var name by remember { mutableStateOf(seed?.name.orEmpty()) }
    var from by remember { mutableStateOf(seed?.let { dateOf(it.start) } ?: today()) }
    var to by remember { mutableStateOf(seed?.let { dateOf(it.end) } ?: today().plusDays(6)) }
    val ok = name.isNotBlank() && !to.isBefore(from)
    val go = {
        val n = name.trim()
        onClose()
        snack.go {
            repo.life.startTrip(n, startOf(from), endOf(to))
            snack.say("Started $n")
        }
    }
    Sheet(onClose) {
        SheetTitle("Start a trip", "Spends in these dates are tagged to it")
        Field("Name", name, { name = it.take(40) }, Modifier.padding(top = 16.dp), ime = ImeAction.Done) { if (ok) go() }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DateField("From", from, Modifier.weight(1f)) { from = it; if (to.isBefore(it)) to = it }
            DateField("To", to, Modifier.weight(1f)) { to = it }
        }
        Btn("Start", Modifier.padding(top = 20.dp), go = true, enabled = ok) { go() }
    }
}

@Composable
private fun EndTrip(t: TripRow, s: Summary?, onClose: () -> Unit) {
    val p = pal
    val repo = LocalContext.current.sl.repo
    val snack = LocalSnack.current
    Sheet(onClose) {
        SheetTitle("End ${t.name}", "Later spends are no longer tagged to it")
        if (s != null) {
            Amount(inr(s.total), Ty.mono(32, FontWeight.Bold).copy(color = p.ink), Modifier.padding(top = 16.dp))
            Text("${inr(s.perDay)} a day over ${s.days} ${if (s.days == 1) "day" else "days"}", Modifier.padding(top = 2.dp), style = Ty.ui(14, FontWeight.Normal).copy(color = p.ink2))
        }
        Btn("End trip", Modifier.padding(top = 20.dp), go = true) {
            onClose()
            snack.go {
                repo.life.endTrip(System.currentTimeMillis())
                snack.say("Ended ${t.name}")
            }
        }
    }
}

@Composable
fun TripScreen(id: Long, back: () -> Unit) {
    val p = pal
    val repo = LocalContext.current.sl.repo
    val scope = rememberCoroutineScope()
    val trips by remember { repo.life.tripFlow() }.collectAsStateWithLifecycle(emptyList())
    val money by repo.money.collectAsStateWithLifecycle(emptyList())
    var rev by remember { mutableStateOf(0) }
    val t = trips.firstOrNull { it.id == id }
    val data by produceState<Pair<Summary, List<TSpend>>?>(null, t, money, rev) {
        value = t?.let { repo.life.summary(it) to repo.life.spends(it) }
    }
    var edit by remember { mutableStateOf<TSpend?>(null) }
    var ending by remember { mutableStateOf(false) }
    val s = data?.first
    val groups = remember(data) { data?.second?.let { TripView.byDay(it, zone()) }.orEmpty() }
    Screen(t?.name ?: "Trip", t?.dates().orEmpty(), back = back, nav = false, tall = false, lead = {
        if (s != null) {
            val top = s.top.map { Bar(it.first, it.second) }
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth().clip(CardShape).background(p.card).padding(20.dp)) {
                Text("Total", style = Ty.ui(13, FontWeight.Medium).copy(color = p.ink2))
                Amount(inr(s.total), Ty.mono(40, FontWeight.Bold).copy(color = p.ink))
                Text("${inr(s.perDay)} a day · ${s.days} ${if (s.days == 1) "day" else "days"}", Modifier.padding(top = 2.dp), style = Ty.ui(14, FontWeight.Normal).copy(color = p.ink2))
                if (top.isNotEmpty()) {
                    CatBar(top, Modifier.padding(top = 16.dp))
                    CatLegend(top, Modifier.padding(top = 14.dp))
                }
                if (t?.active == true) Btn("End trip", Modifier.padding(top = 16.dp)) { ending = true }
            }
        }
    }) {
        if (data != null && groups.isEmpty()) empty(Ic.Trip, "No spends tagged yet", "Payments in these dates are added here as they arrive.")
        groups.forEach { g ->
            item(key = "d${g.day}") {
                Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(dayLabel(g.day), Modifier.weight(1f), style = Ty.ui(14).copy(color = p.ink2))
                    Text(inr(g.total), style = Ty.mono(14, FontWeight.Medium).copy(color = p.ink2))
                }
            }
            itemsIndexed(g.rows, key = { _, r -> "s${r.id}" }) { k, r ->
                Row(
                    Modifier.animateItem().part(p, k == 0, k == g.rows.lastIndex).clickable(role = Role.Button, onClickLabel = "Edit share") { edit = r }
                        .padding(horizontal = 18.dp, vertical = 12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Lead(Ic.of(r.category), Tone.Plain)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(r.title, style = Ty.ui(16, FontWeight.Medium).copy(color = p.ink), maxLines = 1)
                        Text((r.category ?: "other").cap(), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2), maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(inr(r.cost), style = Ty.mono(16, FontWeight.SemiBold).copy(color = p.ink), maxLines = 1)
                        if (r.share < 0.999) Tag("${Split.label(r.share)} of ${inr(r.paise)}", Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        if (t != null) {
            item(key = "del") {
                TextBtn("Delete this trip", Modifier.padding(start = 16.dp, top = 12.dp), color = p.red) { scope.launch { repo.life.dropTrip(t.id); back() } }
            }
        }
    }
    edit?.let { ShareSheet(id, it, { rev++ }) { edit = null } }
    if (ending && t != null) EndTrip(t, s) { ending = false }
}

@Composable
private fun ShareSheet(trip: Long, r: TSpend, changed: () -> Unit, onClose: () -> Unit) {
    val p = pal
    val repo = LocalContext.current.sl.repo
    val snack = LocalSnack.current
    var custom by remember { mutableStateOf("") }
    var share by remember { mutableStateOf(r.share) }
    val typed = Split.custom(custom)
    val value = if (custom.isNotEmpty()) typed else share
    Sheet(onClose) {
        SheetTitle(r.title, "${inr(r.paise)} · count your share of it")
        FlowRow(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Split.presets.forEach { s -> Chip(Split.label(s), custom.isEmpty() && kotlin.math.abs(share - s) < 0.005) { share = s; custom = "" } }
        }
        Field("Custom share in %", custom, { custom = it.filter(Char::isDigit).take(3) }, Modifier.padding(top = 12.dp), keyboard = KeyboardType.Number, mono = true)
        Text(value?.let { "Counts ${inr(Math.round(r.paise * it))}" } ?: "Enter 1 to 100", Modifier.padding(top = 10.dp), style = Ty.mono(14, FontWeight.Medium).copy(color = p.ink2))
        Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Btn("Save", go = true, enabled = value != null) {
                val v = value ?: 1.0
                onClose()
                snack.go { repo.life.setShare(trip, r.id, v); changed() }
            }
            TextBtn("Remove from trip", color = p.red) {
                onClose()
                snack.go { repo.life.untag(trip, r.id); changed(); snack.say("Removed from the trip") }
            }
        }
    }
}
