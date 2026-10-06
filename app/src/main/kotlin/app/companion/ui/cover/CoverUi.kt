package app.companion.ui.cover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.chat.LogReq
import app.companion.chat.ChatSession
import app.companion.chat.Msg
import app.companion.core.Card
import app.companion.core.Cover
import app.companion.core.Focus
import app.companion.core.Margin
import app.companion.core.Meets
import app.companion.core.Now
import app.companion.core.Query
import app.companion.core.Stage
import app.companion.core.Timeline
import app.companion.data.Item
import app.companion.data.Profile
import app.companion.data.dueDate
import app.companion.sl
import app.companion.system.Meetings
import app.companion.system.Prefs
import app.companion.ui.Ty
import app.companion.ui.clock
import app.companion.ui.codeText
import app.companion.ui.copy
import app.companion.ui.dateOf
import app.companion.ui.daysTo
import app.companion.ui.inr
import app.companion.ui.kit.CodeCard
import app.companion.ui.kit.Ic
import app.companion.ui.kit.Ring
import app.companion.ui.kit.motion
import app.companion.ui.num
import app.companion.ui.pal
import app.companion.ui.rememberNow
import app.companion.ui.rememberVoice
import app.companion.ui.screens.CRED
import app.companion.ui.screens.Ev
import app.companion.ui.screens.Input
import app.companion.ui.screens.cap
import app.companion.ui.screens.Tl
import app.companion.ui.screens.beat
import app.companion.ui.screens.rememberGoal
import app.companion.ui.screens.rememberTl
import kotlinx.coroutines.launch
import kotlin.math.abs
import app.companion.ui.money as amt

private class Cov(
    val tl: Tl, val reveal: Boolean, val hide: Boolean, val unlock: (() -> Unit)?, val now: Long, val otp: Item?, val bills: List<Item>, val goal: Int?, val name: String,
) {
    val out = tl.items.filterIsInstance<Ev.Order>().firstOrNull { it.o.stage == Stage.Out }?.o
    val meet = tl.items.filterIsInstance<Ev.Meet>().firstOrNull { it.m.end > now && Meets.mins(it.m.start, now) <= Meets.COVER / Meets.MIN }?.m
    val due = bills.filter { it.dueDate != null }.minByOrNull { it.due ?: Long.MAX_VALUE }
    val dueDays = due?.dueDate?.let { daysTo(it, dateOf(now)) }
    fun show(text: String) = if (reveal && !hide) text else Cover.MASK
}

@Composable
fun CoverPager(reveal: Boolean, unlock: (() -> Unit)?, page: Int = 0) {
    val p = pal
    val c = LocalContext.current
    val repo = c.sl.repo
    val now by rememberNow(60_000)
    val day = dateOf(now)
    val tl = rememberTl(day, day, now)
    val otps by repo.otps().collectAsStateWithLifecycle(emptyList())
    val bills by repo.bills.collectAsStateWithLifecycle(emptyList())
    val prof by repo.profile.collectAsStateWithLifecycle(Profile())
    val goal = rememberGoal()
    val opts = remember { Prefs.get(c) }
    val st = rememberPagerState(page.coerceIn(0, 4)) { 5 }
    val v = Cov(tl, reveal, opts.hide, unlock, now, otps.firstOrNull { Cover.code(it.at, now) && (it.expires ?: 0) > now }, bills, goal, prof.name)
    Column(
        Modifier.fillMaxSize().background(p.bg).windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)).padding(horizontal = 8.dp),
    ) {
        HorizontalPager(st, Modifier.weight(1f), pageSpacing = 8.dp) { i ->
            BoxWithConstraints(Modifier.fillMaxSize()) {
              Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = if (i == 0) Arrangement.Center else Arrangement.Top) {
                when (i) {
                    0 -> NowPage(v)
                    1 -> TodayPage(v)
                    2 -> AskPage(v)
                    3 -> FoodPage(v)
                    else -> BillsPage(v)
                }
              }
            }
        }
        PageDots(st)
    }
}

@Composable
private fun PageDots(st: PagerState) {
    val p = pal
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp, top = 4.dp), horizontalArrangement = Arrangement.Center) {
        repeat(5) { i ->
            val on = i == st.currentPage
            Box(Modifier.padding(horizontal = 3.dp).size(if (on) 18.dp else 6.dp, 6.dp).background(if (on) p.accent else p.line, RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun Head(text: String, warn: Boolean = false) {
    val p = pal
    Text(text, Modifier.padding(bottom = 6.dp), style = Ty.ui(17, FontWeight.Medium).copy(color = if (warn) p.red else p.ink2), maxLines = 2)
}

@Composable
private fun Big(text: String, size: Int = 44) {
    Text(text, style = Ty.mono(size, FontWeight.Bold).copy(color = pal.ink, letterSpacing = 1.sp), maxLines = 1)
}

@Composable
private fun Quiet(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = Ty.ui(16, FontWeight.Normal).copy(color = pal.ink2, lineHeight = 22.sp))
}

@Composable
private fun BigBtn(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, go: Boolean = true, onClick: () -> Unit) {
    val p = pal
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(50)).background(if (go) p.accent else p.accentBox)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) Icon(icon, null, Modifier.padding(end = 10.dp).size(22.dp), tint = if (go) p.onAccent else p.onAccentBox)
        Text(text, style = Ty.ui(17).copy(color = if (go) p.onAccent else p.onAccentBox), maxLines = 1)
    }
}

@Composable
private fun Hidden(v: Cov) {
    Head("Details hidden")
    Quiet("Unlock to see codes, amounts and plans.")
    v.unlock?.let { BigBtn("Unlock", Modifier.padding(top = 16.dp), Ic.Lock, onClick = it) }
}

@Composable
private fun NowPage(v: Cov) {
    val c = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val repo = c.sl.repo
    val cred = remember { c.packageManager.getLaunchIntentForPackage(CRED) }
    when (Now.focus(v.out != null, v.otp != null, v.meet != null, v.dueDays, v.tl.n)) {
        Focus.Out -> {
            val o = v.out!!
            Head("${o.merchant ?: "Order"} is out for delivery")
            val code = o.code
            if (code == null) {
                Big("On the way", 36)
            } else if (!v.reveal) {
                Hidden(v)
            } else {
                app.companion.ui.Secure()
                Big(codeText(code), 48)
                Quiet("Share this with the rider", Modifier.padding(top = 4.dp))
                BigBtn("Copy code", Modifier.padding(top = 16.dp), Ic.Copy) { copy(c, code) }
            }
        }
        Focus.Code -> {
            val i = v.otp!!
            if (!v.reveal) Hidden(v) else CodeCard(i, { scope.launch { repo.notOtp(i.id) } }, { scope.launch { repo.dropOtp(i.id); app.companion.system.Live.refresh(c) } }, big = true)
        }
        Focus.Meet -> {
            val m = v.meet!!
            Head("Meeting ${Meets.until(m.start, v.now)}")
            Text(if (v.reveal) m.title.ifBlank { "Meeting" } else "Meeting", style = Ty.ui(30, FontWeight.Bold).copy(color = pal.ink, lineHeight = 36.sp), maxLines = 3)
            Quiet("${clock(m.start)} to ${clock(m.end)}${if (v.reveal) Meets.source(m)?.let { " · $it" }.orEmpty() else ""}", Modifier.padding(top = 4.dp))
            if (m.join != null) BigBtn("Join", Modifier.padding(top = 16.dp)) { Meetings.open(c, m) }
        }
        Focus.Due -> {
            val b = v.due!!
            Head("${b.title} · ${Cover.due(v.dueDays!!)}", v.dueDays < 0)
            Big(if (b.paise > 0) v.show(amt(b.paise, b.currency)) else "Due")
            BigBtn("Pay", Modifier.padding(top = 16.dp)) { if (cred != null) c.startActivity(cred) else scope.launch { repo.pay(b.id) } }
        }
        Focus.Spent -> {
            Head("Spent today")
            Big(v.show(inr(v.tl.spent)))
            Quiet("${v.tl.n} ${if (v.tl.n == 1) "payment" else "payments"}", Modifier.padding(top = 4.dp))
            when (val m = v.tl.safe) {
                is Margin.Safe -> Quiet("Safe to spend ${v.show(inr(m.perDay))} a day", Modifier.padding(top = 12.dp))
                is Margin.Over -> Quiet("Over budget by ${v.show(inr(m.by))}", Modifier.padding(top = 12.dp))
                null -> {}
            }
        }
    }
}

@Composable
private fun TodayPage(v: Cov) {
    val p = pal
    Head("Today")
    if (!v.reveal) return Hidden(v)
    val rows = Timeline.around(v.tl.items.filter { it !is Ev.Review }, { it.at }, v.now, 4)
    if (rows.isEmpty()) return Quiet("Nothing planned today.")
    var shown = false
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tight = maxWidth - RAIL < 300.dp
        Column {
            rows.forEach { e ->
                if (!shown && (e.at ?: 0L) > v.now) {
                    shown = true
                    Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("NOW", Modifier.width(RAIL), style = Ty.ui(11, FontWeight.ExtraBold).copy(color = p.red, letterSpacing = 1.2.sp))
                        Box(Modifier.weight(1f).height(2.dp).background(p.red, RoundedCornerShape(1.dp)))
                    }
                }
                val b = e.beat()
                val amount = b.amount?.let { v.show(it) }
                val figure: (@Composable () -> Unit)? = amount?.let { a -> { Text(a, style = Ty.mono(16, FontWeight.SemiBold).copy(color = p.ink), maxLines = 1, softWrap = false) } }
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    Text(e.at?.let(::clock) ?: "Due", Modifier.width(RAIL).padding(top = 2.dp), style = Ty.mono(14, FontWeight.Normal).copy(color = p.ink2), maxLines = 1, softWrap = false)
                    Column(Modifier.weight(1f)) {
                        Text(b.title, style = Ty.ui(17, FontWeight.Medium).copy(color = p.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (b.sub.isNotEmpty()) Text(b.sub, style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (tight && figure != null) Box(Modifier.padding(top = 4.dp)) { figure() }
                    }
                    if (!tight && figure != null) Box(Modifier.padding(start = 8.dp)) { figure() }
                }
            }
        }
    }
}

private val RAIL = 44.dp

@Composable
private fun ChipBtn(text: String, onClick: () -> Unit) {
    val p = pal
    Box(
        Modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(50)).background(p.card).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Ty.ui(16, FontWeight.Medium).copy(color = p.ink), maxLines = 1) }
}

@Composable
private fun Mic(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = pal
    Box(modifier.size(96.dp).clip(CircleShape).background(p.accent).clickable(role = Role.Button, onClickLabel = "Speak", onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(Ic.Mic, "Speak", Modifier.size(40.dp), tint = p.onAccent)
    }
}

@Composable
private fun LastAnswer(s: ChatSession, v: Cov) {
    val p = pal
    val m = s.msgs.lastOrNull { it !is Msg.User }
    if (s.busy && s.typing) Quiet("Working it out on this phone", Modifier.padding(top = 16.dp))
    if (m == null || s.typing) return
    Column(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.card).padding(16.dp)) {
        when (m) {
            is Msg.Rule -> {
                val a = m.answer
                Text(a.total?.takeIf { a.query !is Query.CreateReminder }?.let { v.show(inr(it)) } ?: a.says, style = Ty.mono(32, FontWeight.Bold).copy(color = p.ink), maxLines = 2)
                if (a.total != null) Quiet(a.says, Modifier.padding(top = 4.dp))
                a.lines.take(3).forEach { Quiet("${it.title} ${v.show(inr(it.paise))}", Modifier.padding(top = 4.dp)) }
            }
            is Msg.Bot -> Text(m.text, style = Ty.ui(20, FontWeight.Bold).copy(color = p.ink, lineHeight = 26.sp))
            is Msg.Res -> Brief(m.card, v)
            is Msg.Pick -> {
                Text(m.question, style = Ty.ui(18, FontWeight.Medium).copy(color = p.ink, lineHeight = 24.sp))
                m.opts.filter { it.send != null }.forEach { o -> BigBtn(o.label, Modifier.padding(top = 10.dp), go = false) { s.send(o.send!!) } }
            }
            is Msg.Hits -> Text("${m.items.size} matching ${if (m.items.size == 1) "message" else "messages"}", style = Ty.ui(20, FontWeight.Bold).copy(color = p.ink))
            is Msg.Need -> Quiet("Download the chat model in Settings on the main screen to log meals and ask in plain words.")
            is Msg.Note -> Quiet(m.text)
            is Msg.Act -> Quiet("Open the main screen to confirm.")
            is Msg.User -> Unit
        }
    }
}

@Composable
private fun Brief(c: Card, v: Cov) {
    val p = pal
    val (big, line) = when (c) {
        is Card.Spend -> v.show(inr(c.total)) to c.label
        is Card.Bills -> v.show(inr(c.total)) to c.label
        is Card.Meal -> "${num(c.kcal)} kcal" to "${c.slot.cap()} logged"
        is Card.Day -> "${num(c.kcal)} kcal" to (c.goal?.let { "of ${num(it)} today" } ?: "eaten today")
        is Card.Weight -> String.format(java.util.Locale.US, "%.1f kg", c.kg) to "Weight logged"
        is Card.Trip -> v.show(inr(c.total)) to c.name
        is Card.Docs -> "${c.rows.size} saved" to "Open the vault on the main screen to reveal"
        is Card.Facts -> v.show(c.big ?: "${c.rows.size}") to c.label
        is Card.Confirm -> "Confirm" to "Open the main screen to confirm"
    }
    Text(big, style = Ty.mono(32, FontWeight.Bold).copy(color = p.ink), maxLines = 1)
    Quiet(line, Modifier.padding(top = 4.dp))
}

@Composable
private fun AskPage(v: Cov) {
    val s = LocalContext.current.sl.session
    val voice = rememberVoice { t -> t?.let { s.send(it) } }
    var q by remember { mutableStateOf("") }
    DisposableEffect(Unit) {
        s.warm()
        onDispose { s.cool() }
    }
    Head("Ask")
    if (!v.reveal) return Hidden(v)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (voice != null) Mic(Modifier.padding(vertical = 8.dp)) { voice() }
    }
    LastAnswer(s, v)
    FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChipBtn("Log lunch") {
            s.ctx = LogReq(dateOf(System.currentTimeMillis()), "lunch")
            if (voice != null) voice() else q = "I had "
        }
        ChipBtn("Spent today?") { s.send("Spent today") }
        ChipBtn("Next bill") { s.send("Bills due this week") }
    }
    Input(q, { q = it }, { s.send(q); q = "" }, null, false, Modifier)
}

@Composable
private fun FoodPage(v: Cov) {
    val p = pal
    val s = LocalContext.current.sl.session
    val voice = rememberVoice { t -> t?.let { s.send(it) } }
    Head("Food")
    if (!v.reveal) return Hidden(v)
    val goal = v.goal
    val kcal = v.tl.kcal
    Row(verticalAlignment = Alignment.CenterVertically) {
        Ring(if (goal != null) kcal.toFloat() / goal else 0f, 128.dp, 12.dp) { Text(num(if (goal != null) abs(goal - kcal) else kcal), style = Ty.mono(34, FontWeight.Bold).copy(color = p.ink), maxLines = 1) }
        Column(Modifier.padding(start = 16.dp)) {
            Text(if (goal == null) "kcal eaten" else if (goal >= kcal) "kcal left" else "kcal over", style = Ty.ui(18, FontWeight.Medium).copy(color = p.ink))
            if (goal != null) Quiet("of ${num(goal)}")
        }
    }
    BigBtn("Log a meal", Modifier.padding(top = 16.dp), Ic.Mic) {
        s.ctx = LogReq(dateOf(System.currentTimeMillis()))
        if (voice != null) voice()
    }
    LastAnswer(s, v)
}

@Composable
private fun BillsPage(v: Cov) {
    val p = pal
    val c = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val repo = c.sl.repo
    val cred = remember { c.packageManager.getLaunchIntentForPackage(CRED) }
    Head("Bills")
    if (!v.reveal) return Hidden(v)
    val next = v.bills.filter { it.dueDate != null }.take(3)
    if (next.isEmpty()) return Quiet("Nothing due. You're all paid up.")
    next.forEach { b ->
        val days = daysTo(b.dueDate!!, dateOf(v.now))
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(b.title, style = Ty.ui(17, FontWeight.Medium).copy(color = p.ink), maxLines = 1)
                Text(listOfNotNull(b.paise.takeIf { it > 0 }?.let { v.show(amt(it, b.currency)) }, Cover.due(days)).joinToString(" · "), style = Ty.mono(13, FontWeight.Normal).copy(color = if (days < 0) p.red else p.ink2), maxLines = 1)
            }
            Box(
                Modifier.padding(start = 8.dp).heightIn(min = 56.dp).clip(RoundedCornerShape(50)).background(p.accentBox)
                    .clickable(role = Role.Button) { if (cred != null) c.startActivity(cred) else scope.launch { repo.pay(b.id) } }.padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Pay", style = Ty.ui(16).copy(color = p.onAccentBox), maxLines = 1) }
        }
    }
}
