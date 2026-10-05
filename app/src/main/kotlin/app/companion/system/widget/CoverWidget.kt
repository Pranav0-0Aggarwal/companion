package app.companion.system.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.companion.core.Cover
import app.companion.core.Lane
import app.companion.core.Opts
import app.companion.core.Stage
import app.companion.data.Item
import app.companion.data.dueDate
import app.companion.sl
import app.companion.system.Notes
import app.companion.system.Ping
import app.companion.system.Prefs
import app.companion.ui.Voice
import app.companion.ui.clock
import app.companion.ui.codeText
import app.companion.ui.daysTo
import app.companion.ui.inr
import app.companion.ui.money

private val ground = ColorProvider(Color(0xFF000000))
private val card = ColorProvider(Color(0xFF17171A))
private val ink = ColorProvider(Color(0xFFF2F3F5))
private val ink2 = ColorProvider(Color(0xFFA3A8B0))
private val accent = ColorProvider(Color(0xFF7EA6FF))
private val red = ColorProvider(Color(0xFFFF6B61))

internal class Seen(val out: Ping.Order?, val otp: Item?, val bill: Item?, val days: Int?, val total: Long, val n: Int, val name: String, val opts: Opts)

class CoverWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = context.sl.repo
        val now = System.currentTimeMillis()
        val bill = repo.billsNow().firstOrNull { it.due != null }
        val (total, n) = repo.spentToday(now)
        val seen = Seen(
            Ping.orders(context, 0, now).firstOrNull { it.stage == Stage.Out },
            repo.otpsNow(now).firstOrNull { Cover.code(it.at, now) },
            bill, bill?.dueDate?.let { daysTo(it) }, total, n, repo.profileNow().name, Prefs.get(context),
        )
        provideContent { Face(context, seen) }
    }
}

private fun open(c: Context, route: String): Action =
    actionStartActivity(Notes.intent(c, route).setData(Uri.parse("app.companion://$route")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))

private fun copy(code: String): Action = actionRunCallback<CopyAction>(actionParametersOf(codeKey to code))

private class Line(val head: String, val big: String, val foot: String?, val tap: Action, val warn: Boolean = false)

private class Small(val text: String, val tap: Action, val warn: Boolean = false)

@Composable
private fun Face(c: Context, s: Seen) {
    val o = s.opts
    val lanes = Cover.lanes(s.out != null, s.otp != null, s.days, s.n)
    val lines = lanes.map { lane -> line(c, s, lane) to small(c, s, lane) }
    Column(
        GlanceModifier.fillMaxSize().background(ground).cornerRadius(28.dp).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val first = lines.firstOrNull()
        if (first == null) {
            Column(GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp).clickable(open(c, "today"))) {
                Text(Voice.greet(s.name, 0), style = TextStyle(color = ink, fontSize = 24.sp, fontWeight = FontWeight.Bold))
                Text("Nothing needs you", style = TextStyle(color = ink2, fontSize = 16.sp))
            }
        } else {
            Primary(first.first)
            lines.drop(1).take(2).forEach { Row(GlanceModifier.fillMaxWidth().padding(top = 8.dp)) { Secondary(it.second) } }
        }
    }
}

@Composable
private fun Primary(l: Line) {
    Column(GlanceModifier.fillMaxWidth().background(card).cornerRadius(24.dp).padding(horizontal = 18.dp, vertical = 16.dp).clickable(l.tap)) {
        Text(l.head, style = TextStyle(color = if (l.warn) red else ink2, fontSize = 16.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        Spacer(GlanceModifier.height(4.dp))
        Text(l.big, style = TextStyle(color = ink, fontSize = 48.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        if (l.foot != null) {
            Spacer(GlanceModifier.height(4.dp))
            Text(l.foot, style = TextStyle(color = accent, fontSize = 16.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
    }
}

@Composable
private fun Secondary(s: Small) {
    Text(
        s.text,
        GlanceModifier.fillMaxWidth().background(card).cornerRadius(20.dp).padding(horizontal = 18.dp, vertical = 12.dp).clickable(s.tap),
        style = TextStyle(color = if (s.warn) red else ink, fontSize = 18.sp, fontWeight = FontWeight.Medium),
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
    Lane.Due -> {
        val b = s.bill!!
        val d = s.days!!
        Line("${b.title} · ${Cover.due(d)}", if (b.paise > 0) Cover.amount(s.opts, money(b.paise, b.currency)) else "Due", null, open(c, "bills"), d < 0)
    }
    Lane.Spent -> Line("Spent today", Cover.amount(s.opts, inr(s.total)), "${s.n} ${if (s.n == 1) "payment" else "payments"}", open(c, "ledger"))
}

private fun small(c: Context, s: Seen, lane: Lane): Small = when (lane) {
    Lane.Out -> Small("${s.out!!.merchant ?: "Order"} out for delivery", open(c, "today"))
    Lane.Code -> if (!s.opts.lock) Small("Code ready · open to view", open(c, "today")) else Small("${s.otp!!.title} ${codeText(s.otp.code.orEmpty())}", copy(s.otp.code.orEmpty()))
    Lane.Due -> Small("${s.bill!!.title} · ${Cover.due(s.days!!)}", open(c, "bills"), s.days < 0)
    Lane.Spent -> Small("${Cover.amount(s.opts, inr(s.total))} spent · ${s.n}", open(c, "ledger"))
}
