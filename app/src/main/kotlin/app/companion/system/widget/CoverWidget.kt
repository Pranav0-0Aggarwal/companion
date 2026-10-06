package app.companion.system.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.companion.CoverActivity
import app.companion.core.Brief
import app.companion.core.Cover
import app.companion.core.Lane
import app.companion.core.Meeting
import app.companion.core.Meets
import app.companion.core.Opts
import app.companion.core.Stage
import app.companion.data.Item
import app.companion.data.dueDate
import app.companion.sl
import app.companion.system.Joined
import app.companion.system.Meetings
import app.companion.system.Notes
import app.companion.system.Ping
import app.companion.system.Prefs
import app.companion.ui.Voice
import app.companion.ui.clock
import app.companion.ui.codeText
import app.companion.ui.daysTo
import app.companion.ui.hm
import app.companion.ui.inr
import app.companion.ui.money
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import java.time.LocalDate
import androidx.glance.text.TextAlign
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.width
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ground = ColorProvider(Color(0xFF000000))
private val card = ColorProvider(Color(0xFF17171A))
private val ink = ColorProvider(Color(0xFFF2F3F5))
private val ink2 = ColorProvider(Color(0xFFA3A8B0))
private val accent = ColorProvider(Color(0xFF7EA6FF))
private val red = ColorProvider(Color(0xFFFF6B61))
private val onAccent = ColorProvider(Color(0xFF0B1220))

internal class Seen(
    val out: Ping.Order?, val otp: Item?, val bill: Item?, val days: Int?, val total: Long, val n: Int, val name: String, val opts: Opts,
    val meet: Meeting?, val first: Meeting?, val due0: Int, val brief: Boolean, val now: Long,
    val next: Meeting?, val needs: Int, val kcal: Int,
)

class CoverWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = context.sl.repo
        val now = System.currentTimeMillis()
        val bills = repo.billsNow()
        val bill = bills.firstOrNull { it.due != null }
        val due0 = bills.count { it.dueDate?.let { d -> daysTo(d) } == 0 }
        val (total, n) = repo.spentToday(now)
        val soon = withContext(Dispatchers.IO) { Meetings.soon(context, now) }
        val minute = LocalTime.now().let { it.hour * 60 + it.minute }
        val seen = Seen(
            Ping.orders(context, 0, now).firstOrNull { it.stage == Stage.Out },
            repo.otpsNow(now).firstOrNull { Cover.code(it.at, now) },
            bill, bill?.dueDate?.let { daysTo(it) }, total, n, repo.profileNow().name, Prefs.get(context),
            soon.next?.takeIf { Cover.meet(Meets.mins(it.start, now)) }, soon.first, due0, Cover.brief(minute, soon.first != null, due0), now,
            soon.next, repo.needCount(), repo.life.eatenOn(LocalDate.now()).first().sumOf { it.kcal }.roundToInt(),
        )
        provideContent { Face(context, seen) }
    }
}

@OptIn(ExperimentalGlanceApi::class)
private fun open(c: Context, route: String): Action {
    val i = CoverActivity.intent(c, when (route) { "bills" -> 4; "food" -> 3; "ask" -> 2; "ledger" -> 1; else -> 0 })
    return CoverActivity.options(c)?.let { actionStartActivity(i, actionParametersOf(), it) } ?: actionStartActivity(i)
}

private fun join(c: Context, m: Meeting): Action = actionStartActivity(
    Intent(c, Joined::class.java).putExtra(Joined.KEY, Meetings.key(m)).putExtra(Joined.URL, m.join?.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
)

private fun copy(code: String): Action = actionRunCallback<CopyAction>(actionParametersOf(codeKey to code))

private class Line(val head: String, val big: String, val foot: String?, val tap: Action, val warn: Boolean = false)

private class Tile(val label: String, val value: String, val sub: String, val tap: Action, val warn: Boolean = false)

@Composable
private fun Face(c: Context, s: Seen) {
    val hero = Cover.lanes(s.out != null, s.otp != null, s.days, s.n, s.meet != null, s.brief).firstOrNull()?.takeIf { it != Lane.Spent }
    Column(GlanceModifier.fillMaxSize().background(ground).cornerRadius(28.dp).padding(12.dp)) {
        Row(GlanceModifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 8.dp).clickable(open(c, "today")), verticalAlignment = Alignment.CenterVertically) {
            Text(Voice.hello(s.name, LocalTime.now().hour), GlanceModifier.defaultWeight(), style = TextStyle(color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(if (s.needs > 0) "${s.needs} need you" else "All clear", style = TextStyle(color = if (s.needs > 0) accent else ink2, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
        if (hero != null) Hero(line(c, s, hero))
        tiles(c, s, hero).chunked(2).forEach { row ->
            Row(GlanceModifier.fillMaxWidth().defaultWeight().padding(top = 8.dp)) {
                row.forEachIndexed { k, t ->
                    if (k > 0) Spacer(GlanceModifier.width(8.dp))
                    Cell(t, GlanceModifier.defaultWeight().fillMaxHeight())
                }
            }
        }
        Row(GlanceModifier.fillMaxWidth().padding(top = 8.dp)) {
            Pill("Ask", open(c, "ask"), GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            Pill("Log a meal", open(c, "food"), GlanceModifier.defaultWeight())
        }
    }
}

private fun tiles(c: Context, s: Seen, hero: Lane?): List<Tile> {
    val m = s.next
    return listOfNotNull(
        Tile("Spent today", Cover.amount(s.opts, inr(s.total)), "${s.n} ${if (s.n == 1) "payment" else "payments"}", open(c, "ledger")),
        if (hero == Lane.Meet) null else Tile(
            "Next meeting", m?.let { hm(it.start) } ?: "None",
            m?.let { if (s.opts.lock) it.title.ifBlank { "Meeting" } else Meets.until(it.start, s.now) } ?: "rest of today",
            if (m?.join != null) join(c, m) else open(c, "today"),
        ),
        s.bill?.takeIf { hero != Lane.Due }?.let { Tile("Next bill", Cover.due(s.days!!), it.title, open(c, "bills"), s.days < 0) },
        Tile("Food", "${s.kcal}", "kcal today", open(c, "food")),
        Tile("Inbox", "${s.needs}", if (s.needs == 1) "needs you" else "need you", open(c, "today")),
    ).take(4)
}

@Composable
private fun Hero(l: Line) {
    Column(GlanceModifier.fillMaxWidth().background(card).cornerRadius(22.dp).padding(horizontal = 16.dp, vertical = 12.dp).clickable(l.tap)) {
        Text(l.head, style = TextStyle(color = if (l.warn) red else ink2, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        Text(l.big, style = TextStyle(color = ink, fontSize = 34.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        if (l.foot != null) Text(l.foot, style = TextStyle(color = accent, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}

@Composable
private fun Cell(t: Tile, modifier: GlanceModifier) {
    Column(modifier.background(card).cornerRadius(20.dp).padding(horizontal = 14.dp, vertical = 10.dp).clickable(t.tap), verticalAlignment = Alignment.CenterVertically) {
        Text(t.label, style = TextStyle(color = ink2, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        Text(t.value, style = TextStyle(color = if (t.warn) red else ink, fontSize = 24.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Text(t.sub, style = TextStyle(color = ink2, fontSize = 12.sp), maxLines = 1)
    }
}

@Composable
private fun Pill(text: String, tap: Action, modifier: GlanceModifier) {
    Text(
        text,
        modifier.background(accent).cornerRadius(20.dp).padding(vertical = 10.dp).clickable(tap),
        style = TextStyle(color = onAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
        maxLines = 1,
    )
}

private fun line(c: Context, s: Seen, lane: Lane): Line = when (lane) {
    Lane.Out -> {
        val o = s.out!!
        val code = o.code?.takeIf { s.opts.lock }
        Line("${o.merchant ?: "Order"} · out for delivery", code?.let(::codeText) ?: "On the way", code?.let { "Tap to copy · share with the rider" }, if (code != null) copy(code) else open(c, "today"))
    }
    Lane.Code -> if (!s.opts.lock) Line("Code ready", "Open to view", null, open(c, "today")) else {
        val i = s.otp!!
        Line("${i.title} code · until ${clock(i.expires ?: i.at)}", codeText(i.code.orEmpty()), "Tap to copy", copy(i.code.orEmpty()))
    }
    Lane.Meet -> {
        val m = s.meet!!
        Line(
            if (s.opts.lock) m.title.ifBlank { "Meeting" } else "Meeting", Meets.until(m.start, s.now),
            if (m.join != null) "Tap to join" else m.place?.takeIf { s.opts.lock },
            if (m.join != null) join(c, m) else open(c, "today"),
        )
    }
    Lane.Brief -> {
        val f = s.first
        if (f != null) Line("First meeting", hm(f.start), Brief.due(s.due0).takeIf { s.due0 > 0 }, open(c, "today")) else Line("Due today", Brief.bills(s.due0), null, open(c, "bills"))
    }
    Lane.Due -> {
        val b = s.bill!!
        val d = s.days!!
        Line("${b.title} · ${Cover.due(d)}", if (b.paise > 0) Cover.amount(s.opts, money(b.paise, b.currency)) else "Due", null, open(c, "bills"), d < 0)
    }
    Lane.Spent -> Line("Spent today", Cover.amount(s.opts, inr(s.total)), "${s.n} ${if (s.n == 1) "payment" else "payments"}", open(c, "ledger"))
}
