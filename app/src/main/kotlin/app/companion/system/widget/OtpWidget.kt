package app.companion.system.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.companion.MainActivity
import app.companion.data.Item
import app.companion.data.Task
import app.companion.data.dueDate
import app.companion.sl
import app.companion.ui.Voice
import app.companion.ui.clock
import app.companion.ui.codeText
import app.companion.ui.copy
import app.companion.ui.daysTo
import app.companion.ui.inDays
import app.companion.ui.money

private val ground = ColorProvider(Color(0xFFF4F5F7), Color(0xFF000000))
private val card = ColorProvider(Color(0xFFFFFFFF), Color(0xFF17171A))
private val ink = ColorProvider(Color(0xFF111317), Color(0xFFF2F3F5))
private val ink2 = ColorProvider(Color(0xFF5D626B), Color(0xFFA3A8B0))
private val accent = ColorProvider(Color(0xFF2A62DB), Color(0xFF7EA6FF))
private val red = ColorProvider(Color(0xFFC0302A), Color(0xFFFF6B61))
private val codeKey = ActionParameters.Key<String>("code")

class OtpWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = context.sl.repo
        val otp = repo.otpsNow().firstOrNull()
        val bill = repo.billsNow().firstOrNull { it.due != null }
        val name = repo.profileNow().name
        val soon = repo.pending().firstOrNull()?.takeIf { (it.remindAt ?: 0) - System.currentTimeMillis() < 3_600_000L }
        provideContent { Face(otp, bill, name, soon) }
    }
}

@Composable
internal fun Face(otp: Item?, bill: Item?, name: String, soon: Task?) {
    Column(
        GlanceModifier.fillMaxSize().background(ground).cornerRadius(28.dp).padding(10.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (otp == null && bill == null && soon == null) {
            Column(GlanceModifier.padding(horizontal = 8.dp)) {
                Text(Voice.greet(name, 0), style = TextStyle(color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold))
                Text("Nothing needs you", style = TextStyle(color = ink2, fontSize = 14.sp))
            }
        }
        if (otp != null) Code(otp)
        if (soon != null) Soon(soon, otp != null)
        if (bill != null) Due(bill, otp != null || soon != null)
    }
}

@Composable
private fun Code(i: Item) {
    val value = i.code ?: return
    Column(
        GlanceModifier.fillMaxWidth().background(card).cornerRadius(20.dp).padding(horizontal = 14.dp, vertical = 10.dp)
            .clickable(actionRunCallback<CopyAction>(actionParametersOf(codeKey to value))),
    ) {
        Text(listOf(i.title, i.note.ifBlank { null }).filterNotNull().joinToString(" · "), style = TextStyle(color = ink2, fontSize = 13.sp), maxLines = 1)
        Text(codeText(value), style = TextStyle(color = ink, fontSize = 40.sp, fontWeight = FontWeight.Bold))
        Row(GlanceModifier.fillMaxWidth()) {
            Text("Until ${clock(i.expires ?: i.at)}", style = TextStyle(color = ink2, fontSize = 13.sp, fontWeight = FontWeight.Medium), modifier = GlanceModifier.defaultWeight())
            Text("Tap to copy", style = TextStyle(color = accent, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
private fun Soon(t: Task, gap: Boolean) {
    if (gap) Spacer(GlanceModifier.height(8.dp))
    Row(GlanceModifier.fillMaxWidth().background(card).cornerRadius(20.dp).padding(horizontal = 14.dp, vertical = 9.dp)) {
        Text(t.title, style = TextStyle(color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        Text(clock(t.remindAt ?: 0), style = TextStyle(color = ink2, fontSize = 14.sp))
    }
}

@Composable
private fun Due(b: Item, gap: Boolean) {
    val days = daysTo(b.dueDate ?: return)
    if (gap) Spacer(GlanceModifier.height(8.dp))
    Column(GlanceModifier.fillMaxWidth().background(card).cornerRadius(20.dp).padding(horizontal = 14.dp, vertical = 9.dp)) {
        Text("Next bill · ${inDays(days)}", style = TextStyle(color = if (days < 0) red else ink2, fontSize = 12.sp))
        Row(GlanceModifier.fillMaxWidth()) {
            Text(b.title, style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            if (b.paise > 0) Text(money(b.paise, b.currency), style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold))
        }
    }
}

class CopyAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        parameters[codeKey]?.let { copy(context, it) }
    }
}
