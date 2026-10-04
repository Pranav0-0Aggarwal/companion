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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.companion.MainActivity
import app.companion.data.Item
import app.companion.data.dueDate
import app.companion.sl
import app.companion.ui.Voice
import app.companion.ui.clock
import app.companion.ui.codeText
import app.companion.ui.copy
import app.companion.ui.daysTo
import app.companion.ui.inDays
import app.companion.ui.money

private val cover = ColorProvider(Color(0xFF1F3A68), Color(0xFF0F1F3D))
private val paper = ColorProvider(Color(0xFFFFFFFF), Color(0xFF1B2A4A))
private val ink = ColorProvider(Color(0xFF2A2A2A), Color(0xFFF2F0E8))
private val stamp = ColorProvider(Color(0xFFB3261E), Color(0xFFE8776F))
private val foil = ColorProvider(Color(0xFFD9B95F), Color(0xFFD9B95F))
private val white = ColorProvider(Color.White, Color.White)
private val codeKey = ActionParameters.Key<String>("code")

class OtpWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = context.sl.repo
        val otp = repo.otpsNow().firstOrNull()
        val bill = repo.billsNow().firstOrNull { it.due != null }
        val name = repo.profileNow().name
        provideContent {
            Column(
                GlanceModifier.fillMaxSize().background(cover).cornerRadius(24.dp).padding(10.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (otp == null && bill == null) Text(Voice.greet(name, 0), style = TextStyle(color = foil, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                if (otp != null) Coupon(otp)
                if (bill != null) Due(bill)
            }
        }
    }
}

@Composable
private fun Coupon(i: Item) {
    val value = i.code ?: return
    Column(
        GlanceModifier.fillMaxWidth().background(paper).cornerRadius(14.dp).padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(actionRunCallback<CopyAction>(actionParametersOf(codeKey to value))),
    ) {
        Text(
            codeText(value),
            style = TextStyle(color = ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
        )
        Row(GlanceModifier.fillMaxWidth()) {
            Text(i.title, style = TextStyle(color = ink, fontSize = 12.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            Text("expires ${clock(i.expires ?: i.at)}", style = TextStyle(color = stamp, fontSize = 12.sp))
        }
    }
}

@Composable
private fun Due(b: Item) {
    val days = daysTo(b.dueDate ?: return)
    Column(GlanceModifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(b.title, style = TextStyle(color = foil, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Row(GlanceModifier.fillMaxWidth()) {
            Text(inDays(days), style = TextStyle(color = white, fontSize = 13.sp), modifier = GlanceModifier.defaultWeight())
            if (b.paise > 0) Text(money(b.paise, b.currency), style = TextStyle(color = white, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        }
    }
}

class CopyAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        parameters[codeKey]?.let { copy(context, it) }
    }
}
