package app.companion.ui.screens

import android.Manifest
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.ai.Dl
import app.companion.ai.Manifest as Pins
import app.companion.ai.Mode
import app.companion.ai.ModelJobs
import app.companion.chat.Msg
import app.companion.core.Bar
import app.companion.core.Bars
import app.companion.core.Card
import app.companion.core.DocKind
import app.companion.core.DocLine
import app.companion.core.DocState
import app.companion.core.Due
import app.companion.core.Opt
import app.companion.core.Show
import app.companion.data.Revealed
import app.companion.data.credit
import app.companion.data.moved
import app.companion.ui.Ty
import app.companion.ui.Secure
import app.companion.ui.copy
import app.companion.ui.dayLabel
import app.companion.ui.has
import app.companion.ui.inr
import app.companion.ui.kit.Amount
import app.companion.ui.kit.Btn
import app.companion.ui.kit.CardShape
import app.companion.ui.kit.Group
import app.companion.ui.kit.Ic
import app.companion.ui.kit.Ink
import app.companion.ui.kit.Lead
import app.companion.ui.kit.MoneyRow
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Rule
import app.companion.ui.kit.Section
import app.companion.ui.kit.Stamp
import app.companion.ui.kit.Tag
import app.companion.ui.kit.TextBtn
import app.companion.ui.kit.Tone
import app.companion.ui.kit.motion
import app.companion.ui.kit.rise
import app.companion.ui.num
import app.companion.ui.pal
import app.companion.ui.rememberPerms
import app.companion.ui.shortDay
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun UserBubble(text: String) {
    val p = pal
    Row(Modifier.fillMaxWidth().padding(start = 56.dp, end = 16.dp), horizontalArrangement = Arrangement.End) {
        Text(
            text, Modifier.clip(RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)).background(p.accent).padding(horizontal = 16.dp, vertical = 12.dp),
            style = Ty.ui(16, FontWeight.Normal).copy(color = p.onAccent),
        )
    }
}

@Composable
internal fun BotBubble(text: String) {
    val p = pal
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 56.dp)) {
        Text(
            text, Modifier.clip(RoundedCornerShape(6.dp, 22.dp, 22.dp, 22.dp)).background(p.card).padding(horizontal = 16.dp, vertical = 12.dp),
            style = Ty.ui(16, FontWeight.Normal).copy(color = p.ink),
        )
    }
}

@Composable
internal fun Typing() {
    val p = pal
    val on = motion()
    val t = rememberInfiniteTransition(label = "typing")
    Row(Modifier.padding(start = 16.dp)) {
        Row(Modifier.clip(RoundedCornerShape(6.dp, 22.dp, 22.dp, 22.dp)).background(p.card).padding(horizontal = 18.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                val a = if (on) t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(520, delayMillis = i * 140), RepeatMode.Reverse), label = "d$i").value else 0.6f
                Box(Modifier.size(7.dp).graphicsLayer { alpha = a }.background(p.ink2, CircleShape))
            }
        }
    }
}

@Composable
private fun Shell(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.padding(horizontal = 16.dp).fillMaxWidth().rise().clip(CardShape).background(pal.card).padding(18.dp), content = content)
}

@Composable
private fun Label(text: String) = Text(text, style = Ty.ui(13, FontWeight.Medium).copy(color = pal.ink2), maxLines = 2)

@Composable
private fun MiniBars(bars: List<Bar>, modifier: Modifier = Modifier) {
    val p = pal
    val f = Bars.fracs(bars)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        bars.forEachIndexed { i, b ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.label.cap(), Modifier.weight(1f), style = Ty.ui(13, FontWeight.Medium).copy(color = p.ink), maxLines = 1)
                    Text(inr(b.value), Modifier.padding(start = 8.dp), style = Ty.mono(13, FontWeight.Medium).copy(color = p.ink2), maxLines = 1)
                }
                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.line)) {
                    Box(Modifier.fillMaxWidth(f[i]).fillMaxHeight().background(p.accent))
                }
            }
        }
    }
}

@Composable
private fun Big(text: String) = Amount(text, Ty.mono(32, FontWeight.Bold).copy(color = pal.ink), Modifier.padding(top = 4.dp))

@Composable
internal fun ResCard(c: Card, go: (String) -> Unit, change: (Card.Meal) -> Unit) {
    val p = pal
    when (c) {
        is Card.Spend -> Shell {
            Label(c.label)
            Big(inr(c.total))
            Text("${c.count} ${if (c.count == 1) "payment" else "payments"}${c.versus?.let { " · ${it.label}: ${inr(it.value)}" }.orEmpty()}", Modifier.padding(top = 2.dp), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2))
            if (c.bars.isNotEmpty()) MiniBars(c.bars, Modifier.padding(top = 14.dp))
        }
        is Card.Bills -> Shell {
            Label(c.label)
            Big(inr(c.total))
            c.rows.take(5).forEach { r ->
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.title, style = Ty.ui(15, FontWeight.Medium).copy(color = p.ink), maxLines = 1)
                        Text(r.sub, style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2), maxLines = 1)
                    }
                    Text(inr(r.paise), Modifier.padding(start = 8.dp), style = Ty.mono(15, FontWeight.SemiBold).copy(color = p.ink), maxLines = 1)
                }
            }
            TextBtn("Open bills", Modifier.offset(x = (-12).dp)) { go("bills") }
        }
        is Card.Meal -> Shell {
            val d = LocalDate.ofEpochDay(c.day)
            Label("${c.slot.cap()} logged${if (d != LocalDate.now()) " · ${dayLabel(d)}" else ""}")
            Big("${num(c.kcal)} kcal")
            c.items.forEach { i ->
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(i.name.cap(), style = Ty.ui(15, FontWeight.Medium).copy(color = p.ink), maxLines = 1)
                        Text(i.qty, style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2), maxLines = 1)
                    }
                    if (i.est) Tag("estimate", Modifier.padding(end = 8.dp))
                    Text(num(i.kcal), style = Ty.mono(15, FontWeight.SemiBold).copy(color = p.ink), maxLines = 1)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Day ${num(c.dayKcal)}${c.goal?.let { " of ${num(it)}" }.orEmpty()} kcal", Modifier.weight(1f), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2))
                Btn("Change", dense = true) { change(c) }
            }
        }
        is Card.Day -> Shell {
            Label(dayLabel(LocalDate.ofEpochDay(c.day)))
            Big("${num(c.kcal)} kcal")
            Text(c.goal?.let { "of ${num(it)} goal" } ?: "No goal set", Modifier.padding(top = 2.dp), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2))
            c.meals.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Text(m.label.cap(), Modifier.weight(1f), style = Ty.ui(15, FontWeight.Medium).copy(color = p.ink))
                    Text("${num(m.value.toInt())} kcal", style = Ty.mono(15, FontWeight.SemiBold).copy(color = p.ink2))
                }
            }
            TextBtn("Open food", Modifier.offset(x = (-12).dp)) { go("food") }
        }
        is Card.Weight -> Shell {
            Label("Weight logged · ${shortDay(LocalDate.ofEpochDay(c.day))}")
            Big(String.format(Locale.US, "%.1f kg", c.kg))
            val trend = listOfNotNull(c.perWeek?.let { String.format(Locale.US, "%+.1f kg a week", it) }, c.avg7?.let { String.format(Locale.US, "7 day average %.1f", it) }).joinToString(" · ")
            if (trend.isNotEmpty()) Text(trend, Modifier.padding(top = 2.dp), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2))
        }
        is Card.Trip -> Shell(Modifier.clickable { go("trip/${c.id}") }) {
            Label(c.name)
            Big(inr(c.total))
            Text("${inr(c.perDay)} a day · ${c.days} ${if (c.days == 1) "day" else "days"}", Modifier.padding(top = 2.dp), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2))
            if (c.top.isNotEmpty()) MiniBars(c.top.take(3), Modifier.padding(top = 14.dp))
        }
        is Card.Docs -> DocsCard(c)
    }
}

internal fun kindIcon(kind: String) = when (kind) {
    "Insurance" -> Ic.Shield
    "Rc" -> Ic.Car
    "Puc" -> Ic.Check
    "Fastag" -> Ic.Swap
    "Loan" -> Ic.Money
    "Warranty" -> Ic.Bag
    else -> Ic.Vault
}

internal fun kindLabel(kind: String) = DocKind.entries.firstOrNull { it.name == kind }?.label ?: kind

@Composable
internal fun ExpiryChip(days: Int?, date: LocalDate?, quiet: Boolean = true) {
    if (!quiet && DocState.of(days) == Due.Ok) return
    val t = DocState.chip(days, date) ?: return
    Stamp(t, ink = when (DocState.of(days)) { Due.Expired -> Ink.Red; Due.Soon -> Ink.Amber; else -> Ink.Quiet })
}

@Composable
private fun DocsCard(c: Card.Docs) {
    val p = pal
    val ctx = LocalContext.current
    val reveal = rememberReveal()
    var shown by remember { mutableStateOf<Pair<Long, Revealed>?>(null) }
    AutoHide(shown != null) { shown = null }
    shown?.let { Secure() }
    Group(Modifier.rise()) {
        c.rows.forEachIndexed { i, r ->
            if (i > 0) Rule(72.dp)
            val open = shown?.takeIf { it.first == r.id }?.second
            PassLine(
                r.title, listOfNotNull(kindLabel(r.kind), if (open == null) r.mask else open.number).joinToString(" · "), lead = kindIcon(r.kind), tone = Tone.Plain, lines = 1,
                trailing = {
                    Column(horizontalAlignment = Alignment.End) {
                        if (open == null) {
                            Btn("Reveal", dense = true) { reveal(r.id) { d -> if (d != null) shown = r.id to d } }
                        } else {
                            Btn("Copy", dense = true) { open.number?.let { copy(ctx, it, "vault") } }
                        }
                        Box(Modifier.padding(top = 6.dp)) { ExpiryChip(r.days, null) }
                    }
                },
            )
        }
    }
}

@Composable
internal fun PickCard(m: Msg.Pick, title: String? = null, send: (Opt) -> Unit) {
    val p = pal
    Shell {
        if (title != null) Label(title)
        Text(m.question, Modifier.padding(top = if (title != null) 4.dp else 0.dp), style = Ty.ui(16, FontWeight.Medium).copy(color = p.ink))
        if (m.opts.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 12.dp).graphicsLayer { alpha = if (m.done) 0.4f else 1f }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                m.opts.forEach { o -> Btn(o.label, enabled = !m.done) { send(o) } }
            }
        }
    }
}

@Composable
internal fun NeedCard() {
    val p = pal
    val c = LocalContext.current
    val s by Dl.state.collectAsStateWithLifecycle()
    val askNotif = rememberPerms(Manifest.permission.POST_NOTIFICATIONS) {}
    val busy = s.mode == Mode.Running || s.mode == Mode.Waiting
    Shell {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Lead(Ic.Download, Tone.Accent)
            Column(Modifier.padding(start = 14.dp)) {
                Text("Chat model not installed", style = Ty.ui(16, FontWeight.Medium).copy(color = p.ink))
                Text(
                    if (busy) (if (s.mode == Mode.Waiting) "Waiting for Wi-Fi" else "Downloading ${Show.pct(s.ready + s.pos, Pins.chat.bytes)}%") else String.format(Locale.US, "%.1f GB, downloads on Wi-Fi only", Pins.chat.bytes / 1e9),
                    style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2),
                )
            }
        }
        Text("Spending and bills answers work without it. The model lets you log meals and ask in plain words.", Modifier.padding(top = 12.dp), style = Ty.ui(14, FontWeight.Normal).copy(color = p.ink2))
        if (!busy) {
            Btn("Download", Modifier.padding(top = 12.dp), go = true, icon = Ic.Download) {
                ModelJobs.start(c)
                if (!c.has(Manifest.permission.POST_NOTIFICATIONS)) askNotif()
            }
        }
    }
}

@Composable
internal fun HitsCard(items: List<app.companion.data.Item>, go: (String) -> Unit) {
    val p = pal
    Column {
        Section("In your messages · ${items.size}")
        Group {
            items.take(6).forEachIndexed { i, it ->
                if (i > 0) Rule(72.dp)
                if (it.paise > 0) MoneyRow(it.shown(), it.meta().joinToString(" · "), app.companion.ui.money(it.paise, it.currency), it.credit, lead = Ic.src(it.src), moved = it.credit && it.moved)
                else PassLine(it.title, it.meta().joinToString(" · "), lead = Ic.src(it.src), tone = Tone.Plain)
            }
        }
    }
}
