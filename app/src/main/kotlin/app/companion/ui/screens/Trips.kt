package app.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.core.Pick
import app.companion.core.TripSummary
import app.companion.data.TripRow
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.dateOf
import app.companion.ui.inr
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Field
import app.companion.ui.kit.Ic
import app.companion.ui.kit.Ink
import app.companion.ui.kit.LocalSnack
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Screen
import app.companion.ui.kit.Section
import app.companion.ui.kit.Stamp
import app.companion.ui.kit.TextBtn
import app.companion.ui.kit.ToolButton
import app.companion.ui.kit.Tone
import app.companion.ui.kit.empty
import app.companion.ui.kit.part
import app.companion.ui.pal
import app.companion.ui.shortDay
import app.companion.ui.span
import app.companion.ui.today
import app.companion.ui.zone
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch

private fun startOf(d: LocalDate) = d.atStartOfDay(zone()).toInstant().toEpochMilli()

private fun endOf(d: LocalDate) = d.plusDays(1).atStartOfDay(zone()).toInstant().toEpochMilli() - 1

@Composable
fun TripsScreen(lead: @Composable () -> Unit) {
    val p = pal
    val repo = LocalContext.current.sl.repo
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val trips by remember { repo.life.tripFlow() }.collectAsStateWithLifecycle(emptyList())
    val money by repo.money.collectAsStateWithLifecycle(emptyList())
    val sums by produceState(emptyMap<Long, app.companion.core.Summary>(), trips, money) { value = trips.associate { it.id to repo.life.summary(it) } }
    val picks by produceState(emptyList<Pick>(), trips) { value = repo.life.suggestions(System.currentTimeMillis()) }
    var sheet by remember { mutableStateOf(false) }
    var seed by remember { mutableStateOf<Pick?>(null) }
    val total = sums.values.sumOf { it.total }
    Screen(
        "Money", if (trips.isEmpty()) "No trips yet" else "${trips.size} ${if (trips.size == 1) "trip" else "trips"} · ${inr(total)}", lead = lead,
        tools = { ToolButton(Ic.Add, "Start a trip") { seed = null; sheet = true } },
    ) {
        if (trips.isEmpty()) {
            empty(Ic.Trip, "No trips yet", "Start a trip and every spend in its dates is tagged to it.") {
                Btn("Start a trip", go = true, icon = Ic.Add) { seed = null; sheet = true }
            }
        }
        if (picks.isNotEmpty()) {
            item(key = "ph") { Section("Looks like a trip") }
            itemsIndexed(picks, key = { _, t -> "p${t.name}${t.start}" }) { k, t ->
                androidx.compose.foundation.layout.Box(Modifier.animateItem().part(p, k == 0, k == picks.lastIndex)) {
                    PassLine(
                        t.name, span(dateOf(t.start), dateOf(t.end)), lead = Ic.Trip, tone = Tone.Plain, lines = 1,
                        trailing = { Btn("Start", dense = true) { seed = t; sheet = true } },
                    )
                }
            }
        }
        if (trips.isNotEmpty()) item(key = "th") { Section("Trips") }
        itemsIndexed(trips, key = { _, t -> "t${t.id}" }) { k, t ->
            androidx.compose.foundation.layout.Box(Modifier.animateItem().part(p, k == 0, k == trips.lastIndex)) {
                val s = sums[t.id]
                PassLine(
                    t.name, "${span(dateOf(t.start), dateOf(t.end))}${s?.let { " · ${it.days} ${if (it.days == 1) "day" else "days"}" }.orEmpty()}",
                    lead = Ic.Trip, tone = if (t.active) Tone.Accent else Tone.Plain, lines = 1,
                    tags = s?.top?.take(3)?.map { "${it.first} ${inr(it.second)}" }.orEmpty(),
                    trailing = {
                        Column(horizontalAlignment = Alignment.End) {
                            if (s != null) {
                                Text(inr(s.total), style = Ty.mono(16, FontWeight.SemiBold).copy(color = p.ink), maxLines = 1)
                                Text("${inr(s.perDay)} a day", style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2), maxLines = 1)
                            }
                            if (t.active) Stamp("ACTIVE", Modifier.padding(top = 6.dp), ok = true)
                        }
                    },
                    actions = if (t.active) { { TextBtn("End trip") { scope.launch { repo.life.endTrip(System.currentTimeMillis()) } } } } else null,
                )
            }
        }
    }
    if (sheet) StartTrip(seed) { sheet = false }
}

private fun LocalDate.utc() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.utcDay() = java.time.Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, d: LocalDate, onPick: (LocalDate) -> Unit) {
    val p = pal
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).background(p.raised).clickable(onClickLabel = label) { open = true }.padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, style = Ty.ui(13, FontWeight.Medium).copy(color = p.ink2))
        Text("${app.companion.ui.weekday(d)} ${shortDay(d)}", Modifier.padding(top = 2.dp), style = Ty.mono(16, FontWeight.Medium).copy(color = p.ink))
    }
    if (open) {
        val st = rememberDatePickerState(initialSelectedDateMillis = d.utc())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextBtn("Done") { st.selectedDateMillis?.let { onPick(it.utcDay()) }; open = false } },
            dismissButton = { TextBtn("Cancel", color = p.ink2) { open = false } },
        ) { DatePicker(st) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartTrip(seed: Pick?, onClose: () -> Unit) {
    val p = pal
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
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = p.bg, contentColor = p.ink) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 24.dp).padding(bottom = 20.dp)) {
            Text("Start a trip", style = Ty.ui(22, FontWeight.Bold).copy(color = p.ink))
            Text("Spends in these dates are tagged to it", Modifier.padding(top = 4.dp), style = Ty.ui(14, FontWeight.Normal).copy(color = p.ink2))
            Field("Name", name, { name = it.take(40) }, Modifier.padding(top = 16.dp), ime = ImeAction.Done) { if (ok) go() }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) { DateField("From", from) { from = it; if (to.isBefore(it)) to = it } }
                Column(Modifier.weight(1f)) { DateField("To", to) { to = it } }
            }
            Btn("Start", Modifier.padding(top = 20.dp), go = true, enabled = ok) { go() }
        }
    }
}
