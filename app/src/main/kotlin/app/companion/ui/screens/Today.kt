package app.companion.ui.screens

import android.Manifest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.chat.LogReq
import app.companion.core.Timeline
import app.companion.data.Profile
import app.companion.system.FoodPrefs
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.Voice
import app.companion.ui.dayLabel
import app.companion.ui.has
import app.companion.ui.inr
import app.companion.ui.kit.Amount
import app.companion.ui.kit.Btn
import app.companion.ui.kit.CardShape
import app.companion.ui.kit.DayStrip
import app.companion.ui.kit.Empty
import app.companion.ui.kit.Ic
import app.companion.ui.kit.LocalSnack
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Ring
import app.companion.ui.kit.Screen
import app.companion.ui.kit.Section
import app.companion.ui.kit.ToolButton
import app.companion.ui.kit.Tone
import app.companion.ui.kit.part
import app.companion.ui.pal
import app.companion.ui.rememberNow
import app.companion.ui.rememberPerms
import app.companion.ui.today
import java.time.LocalDate
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import app.companion.ui.money as amt

@Composable
fun TodayScreen(go: (String) -> Unit) {
    val p = pal
    val repo = LocalContext.current.sl.repo
    val snack = LocalSnack.current
    val ask = LocalAsk.current
    val d = rememberDay()
    var sel by rememberSaveable { mutableStateOf<Long?>(null) }
    val day = sel?.let(LocalDate::ofEpochDay) ?: d.today
    val live = day == d.today
    val now by rememberNow(60_000)
    val clock by rememberNow(1000, active = d.otps.isNotEmpty())
    val codes by remember(d.otps) { derivedStateOf(structuralEqualityPolicy()) { d.otps.filter { (it.expires ?: 0) > clock } } }
    val tl = rememberTl(day, d.today, now)
    val goal = rememberGoal()
    val c = LocalContext.current
    var sure by remember { mutableStateOf(FoodPrefs.sure(c)) }
    val nowAt = if (live) Timeline.nowAt(tl.items, { it.at }, now) else if (day < d.today) tl.items.size else 0
    val rel = app.companion.ui.daysTo(day, d.today)
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box {
        Screen(
            if (live) d.hello else Voice.rel(rel).takeIf { rel in -1..1 } ?: dayLabel(day),
            if (live) d.sub else if (rel in -1..1) dayLabel(day) else Voice.rel(rel),
            bar = if (live) "Today" else dayLabel(day),
            tall = false,
            foot = 66.dp,
            lead = {
                Column {
                    DayStrip(day, d.today, Modifier.padding(top = 4.dp)) { sel = it.takeIf { x -> x != d.today }?.toEpochDay() }
                    Glance(tl, day, live, goal, go)
                }
            },
        ) {
            item(key = "proc", contentType = "proc") { ProcessSlot() }
            item(key = "hear", contentType = "hear") { Hearing() }
            if (live) {
                itemsIndexed(codes, key = { _, o -> "o${o.id}" }, contentType = { _, _ -> "code" }) { _, o ->
                    app.companion.ui.kit.CodeCard(o, { snack.teach(repo) { repo.notOtp(o.id) } }, Modifier.animateItem().padding(start = 16.dp, end = 16.dp, top = 12.dp))
                }
            }
            if (tl.items.isEmpty()) {
                item(key = "clear") {
                    if (live) PassLine("All clear", "Nothing needs you right now", Modifier.padding(top = 16.dp).part(p, true, true), lead = Ic.Check, tone = Tone.Green)
                    else Empty(Ic.Today, "Nothing on ${dayLabel(day)}", "Meals, payments and plans for this day show here.", action = { Btn("Log a meal", icon = Ic.Add) { ask(AskReq(null, "I had ", log = LogReq(day))) } })
                }
            }
            item(key = "gap") { Box(Modifier.padding(top = 8.dp)) }
            tl.items.forEachIndexed { k, e ->
                if (live && k == nowAt) item(key = "now") { NowLine() }
                item(key = e.key, contentType = e::class.simpleName) {
                    Box(Modifier.animateItem()) { TlRow(e, k < nowAt, now, d.today, go, { ask(AskReq(null, "I had ", log = it)) }, sure) { sure = sure + it } }
                }
            }
            if (live && tl.items.isNotEmpty() && nowAt == tl.items.size) item(key = "now") { NowLine() }
            if (tl.soon.isNotEmpty()) {
                item(key = "soonh") { Section("Coming up") }
                itemsIndexed(tl.soon, key = { _, e -> "c${e.key}" }) { k, e ->
                    val b = e.beat()
                    Box(Modifier.animateItem().part(p, k == 0, k == tl.soon.lastIndex)) {
                        PassLine(b.title, b.sub, lead = if (e is Ev.Bill) Ic.Bolt else if (e is Ev.Meet) Ic.Calendar else Ic.Bell, tone = Tone.Plain, onClick = { go(if (e is Ev.Bill) "bills" else "plan") },
                            trailing = b.amount?.let { a -> { Text(a, style = Ty.mono(16, FontWeight.SemiBold).copy(color = p.ink)) } })
                    }
                }
            }
            if (live) item(key = "cal") { CalAsk() }
            if (live) item(key = "sugg") { Suggested() }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(p.bg).padding(top = 8.dp, bottom = nav + 86.dp)) { AskPill(hint = "Ask or log anything") }
    }
}

@Composable
private fun CalAsk() {
    val c = LocalContext.current
    var ok by remember { mutableStateOf(c.has(Manifest.permission.READ_CALENDAR)) }
    val ask = rememberPerms(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR) { ok = c.has(Manifest.permission.READ_CALENDAR) }
    if (ok) return
    PassLine("Show meetings here", "Reads your calendar on this phone only", Modifier.padding(top = 16.dp).part(pal, true, true), lead = Ic.Calendar, tone = Tone.Plain, trailing = { Btn("Allow", dense = true) { ask() } })
}

@Composable
internal fun GlanceTile(label: String, modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    val p = pal
    Column(
        modifier.clip(CardShape).background(p.card).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(label, Modifier.padding(bottom = 6.dp), style = Ty.ui(13, FontWeight.Medium).copy(color = p.ink2), maxLines = 1)
        content()
    }
}

@Composable
private fun Glance(t: Tl, day: LocalDate, live: Boolean, goal: Int?, go: (String) -> Unit) {
    val p = pal
    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val f = t.fig(live)
        GlanceTile(f.label, Modifier.weight(1f), { go("ledger") }) {
            Amount(f.figure, Ty.mono(28, FontWeight.Bold).copy(color = if (f.over) p.red else p.ink))
            Text(f.sub, style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2), maxLines = 3)
        }
        if (goal != null) {
            val left = goal - t.kcal
            GlanceTile("Calories", Modifier.weight(1f), { go("food") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ring(t.kcal.toFloat() / goal, 48.dp, 6.dp) {}
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(app.companion.ui.num(kotlin.math.abs(left)), style = Ty.mono(22, FontWeight.Bold).copy(color = p.ink), maxLines = 1)
                        Text(if (left >= 0) "left" else "over", style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun rememberGoal(): Int? {
    val sl = LocalContext.current.sl
    val prof by sl.repo.profile.collectAsStateWithLifecycle(Profile())
    return androidx.compose.runtime.produceState<Int?>(null, prof) { value = sl.chat.foods.target() }.value
}
