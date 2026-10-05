package app.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.companion.chat.LogReq
import app.companion.core.Meets
import app.companion.core.Stage
import app.companion.core.Track
import app.companion.data.brand
import app.companion.data.credit
import app.companion.data.dueDate
import app.companion.data.moved
import app.companion.sl
import app.companion.system.FoodPrefs
import app.companion.system.Meetings
import app.companion.system.Plan
import app.companion.ui.Pick
import app.companion.ui.Ty
import app.companion.ui.Voice
import app.companion.ui.clock
import app.companion.ui.codeText
import app.companion.ui.copy
import app.companion.ui.daysTo
import app.companion.ui.hm
import app.companion.ui.inDays
import app.companion.ui.Secure
import app.companion.ui.kit.Btn
import app.companion.ui.kit.CardShape
import app.companion.ui.kit.Ic
import app.companion.ui.kit.Ink
import app.companion.ui.kit.MoneyRow
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.StateStamp
import app.companion.ui.kit.TextBtn
import app.companion.ui.kit.Tone
import app.companion.ui.pal
import app.companion.ui.shortDay
import java.time.LocalDate
import kotlinx.coroutines.launch
import app.companion.ui.money as amt

class Beat(val title: String, val sub: String, val amount: String?)

fun Ev.beat(): Beat = when (this) {
    is Ev.Meal -> {
        val m = e.meal
        Beat(m.slot.cap(), if (e.items.isEmpty()) "Not logged" else "${Math.round(e.kcal)} kcal", paid?.let { amt(it.paise, it.currency) })
    }
    is Ev.Spend -> Beat(i.shown(), i.meta().joinToString(" · "), (if (i.credit && !i.moved) "+" else "") + amt(i.paise, i.currency))
    is Ev.Meet -> Beat(m.title.ifBlank { "Meeting" }, "${hm(m.start)} to ${hm(m.end)}", null)
    is Ev.Order -> Beat("${o.merchant ?: "Order"} order", Track.word(o.stage), null)
    is Ev.Bill -> Beat(b.title, b.dueDate?.let { "Due ${shortDay(it)}" }.orEmpty(), b.paise.takeIf { it > 0 }?.let { amt(it, b.currency) })
    is Ev.Chore -> Beat(t.title, t.remindAt?.let(::clock).orEmpty(), null)
    is Ev.Review -> Beat("$n need you", "Open Inbox", null)
}

private const val X = 66

private fun Modifier.rail(line: Color, dot: Color, ring: Color, y: Dp, r: Dp = 4.5.dp): Modifier = drawBehind {
    val x = X.dp.toPx()
    drawRect(line, Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
    drawCircle(ring, r.toPx() + 2.5.dp.toPx(), Offset(x, y.toPx()))
    drawCircle(dot, r.toPx(), Offset(x, y.toPx()))
}

@Composable
fun NowLine() {
    val p = pal
    Row(Modifier.fillMaxWidth().height(26.dp).rail(p.line, p.red, p.bg, 13.dp).padding(start = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("NOW", Modifier.width(44.dp), style = Ty.ui(11, FontWeight.ExtraBold).copy(color = p.red, letterSpacing = 1.2.sp), maxLines = 1)
        Box(Modifier.width(20.dp))
        Box(Modifier.weight(1f).height(2.dp).background(p.red, RoundedCornerShape(1.dp)))
    }
}

@Composable
fun Rail(label: String, dot: Color, content: @Composable () -> Unit) {
    val p = pal
    Row(Modifier.fillMaxWidth().rail(p.line, dot, p.bg, 30.dp).padding(start = 12.dp, end = 16.dp)) {
        Text(label, Modifier.width(44.dp).padding(top = 22.dp), style = Ty.mono(13, FontWeight.Normal).copy(color = p.ink2), maxLines = 1)
        Box(Modifier.width(20.dp))
        Box(Modifier.weight(1f).padding(bottom = 8.dp).clip(CardShape).background(p.card)) { content() }
    }
}

@Composable
fun TlRow(e: Ev, past: Boolean, now: Long, today: LocalDate, go: (String) -> Unit, log: (LogReq) -> Unit, sure: Set<Long>, onSure: (Long) -> Unit) {
    val p = pal
    val label = when (e) {
        is Ev.Bill -> "Due"
        is Ev.Review -> ""
        else -> e.at?.let(::clock).orEmpty()
    }
    Rail(label, if (past) p.ink3 else p.accent) {
        when (e) {
            is Ev.Meal -> MealCard(e, e.e.meal.id in sure, log, onSure)
            is Ev.Spend -> e.i.let { i ->
                MoneyRow(
                    i.shown(), i.meta().joinToString(" · "), amt(i.paise, i.currency), i.credit,
                    lead = Ic.of(i.category), brand = i.brand, stamp = i.stamp(), tags = i.tagged(), moved = i.credit && i.moved, onClick = { Pick.item.value = i.id; go("ledger") },
                )
            }
            is Ev.Meet -> MeetCard(e, now)
            is Ev.Order -> OrderCard(e)
            is Ev.Bill -> BillCard(e, today, go)
            is Ev.Chore -> ChoreCard(e)
            is Ev.Review -> PassLine(
                Voice.need(e.n).cap(), "Look them over in Inbox", lead = Ic.Inbox, tone = Tone.Red, lines = 1,
                trailing = { Btn("Review", dense = true, go = true) { go("inbox") } },
            )
        }
    }
}

@Composable
private fun MealCard(e: Ev.Meal, sure: Boolean, log: (LogReq) -> Unit, onSure: (Long) -> Unit) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val m = e.e.meal
    val slot = m.slot.cap()
    val paid = e.paid
    val ask = LogReq(LocalDate.ofEpochDay(m.day), m.slot, if (m.src == "order") m.id else null)
    if (e.e.items.isEmpty()) {
        PassLine(
            "$slot not logged", paid?.let { "${amt(it.paise, it.currency)} at ${it.merchant ?: it.title}" } ?: m.note.orEmpty(),
            lead = Ic.Food, brand = paid?.brand, tone = Tone.Plain, lines = 1,
            trailing = { Btn("Log", dense = true, go = true) { log(ask) } },
        )
        return
    }
    val names = e.e.items.joinToString(", ") { it.name }
    val ok = m.src != "order" || sure
    PassLine(
        if (m.src == "order" && !m.note.isNullOrBlank()) "$slot from ${m.note}" else slot,
        "Logged ${Math.round(e.e.kcal)} kcal · $names",
        lead = Ic.Food, brand = paid?.brand, tone = Tone.Plain, lines = 1, fill = true,
        trailing = paid?.let { { Text(amt(it.paise, it.currency), style = Ty.mono(16, FontWeight.SemiBold).copy(color = pal.ink)) } },
        actions = if (ok) null else {
            {
                Btn("Looks right", Modifier.weight(1f), go = true, dense = true) { FoodPrefs.sure(c, m.id); onSure(m.id) }
                Btn("Half", Modifier.weight(1f), dense = true) { scope.launch { c.sl.repo.life.scale(m.id, 0.5); FoodPrefs.sure(c, m.id); onSure(m.id) } }
                Btn("Change", Modifier.weight(1f), dense = true) { log(ask) }
            }
        },
    )
}

@Composable
private fun MeetCard(e: Ev.Meet, now: Long) {
    val c = LocalContext.current
    val m = e.m
    PassLine(
        m.title.ifBlank { "Meeting" },
        listOfNotNull("${hm(m.start)} to ${hm(m.end)}", Meets.source(m), e.clash?.let { "Overlaps $it" }).joinToString(" · "),
        lead = Ic.Calendar, lines = 1,
        trailing = { if (m.join != null && m.end > now) Btn("Join", dense = true, go = true) { Meetings.open(c, m) } },
    )
}

@Composable
private fun OrderCard(e: Ev.Order) {
    val o = e.o
    PassLine(
        "${o.merchant ?: "Order"} order", Track.word(o.stage).replaceFirstChar(Char::uppercase),
        brand = o.merchant, lead = Ic.Cart, lines = 1,
        trailing = { if (o.stage == Stage.Out && o.code != null) CodeChip(o.code) },
    )
}

@Composable
fun CodeChip(code: String) {
    Secure()
    val p = pal
    val c = LocalContext.current
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(p.accentBox).clickable(onClickLabel = "Copy code") { copy(c, code) }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(codeText(code), style = Ty.mono(16, FontWeight.Bold).copy(color = p.onAccentBox, letterSpacing = 1.sp), maxLines = 1)
        Icon(Ic.Copy, null, Modifier.padding(start = 8.dp).size(16.dp), tint = p.onAccentBox)
    }
}

@Composable
private fun BillCard(e: Ev.Bill, today: LocalDate, go: (String) -> Unit) {
    val c = LocalContext.current
    val b = e.b
    val due = b.dueDate!!
    val days = daysTo(due, today)
    val cred = androidx.compose.runtime.remember { c.packageManager.getLaunchIntentForPackage(CRED) }
    PassLine(
        b.title,
        listOfNotNull(b.paise.takeIf { it > 0 }?.let { amt(it, b.currency) }, b.minPaise?.let { "min ${amt(it, b.currency)}" }, if (e.paid) null else inDays(days)).joinToString(" · "),
        lead = Ic.Bolt, brand = b.brand, tone = if (e.paid) Tone.Green else if (days < 0) Tone.Red else Tone.Accent, lines = 1, fill = !e.paid,
        trailing = { StateStamp(if (e.paid) "PAID" else if (days < 0) "OVERDUE" else "DUE", if (e.paid) Ink.Green else Ink.Red) },
        actions = if (e.paid) null else {
            {
                Btn("Pay", Modifier.weight(1f), go = true, dense = true) { if (cred != null) c.startActivity(cred) else go("bills") }
                RemindBtn(b.title, due, Modifier.weight(1f))
            }
        },
    )
}

@Composable
private fun ChoreCard(e: Ev.Chore) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val t = e.t
    PassLine(
        t.title, t.remindAt?.let(::clock).orEmpty(), lead = if (t.done) Ic.Check else Ic.Bell, tone = if (t.done) Tone.Green else Tone.Plain, lines = 1,
        trailing = { if (!t.done) TextBtn("Done") { scope.launch { Plan.finish(c, t.id) } } },
    )
}
