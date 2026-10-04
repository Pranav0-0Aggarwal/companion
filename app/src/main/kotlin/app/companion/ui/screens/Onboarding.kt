package app.companion.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.companion.data.Profile
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.Voice
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Group
import app.companion.ui.kit.Ic
import app.companion.ui.kit.IconRow
import app.companion.ui.kit.Motion
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Rule
import app.companion.ui.kit.TextBtn
import app.companion.ui.kit.motion
import app.companion.ui.pal
import kotlinx.coroutines.launch

private const val LAST = 3

@Composable
fun OnboardingScreen() {
    val p = pal
    val c = LocalContext.current
    val repo = c.sl.repo
    val scope = rememberCoroutineScope()
    val on = motion()
    var step by remember { mutableIntStateOf(0) }
    var about by remember { mutableStateOf(AboutYou()) }
    var src by remember { mutableStateOf(Profile(sms = false, notif = false)) }
    val name = (about.call ?: about.first).trim()
    val ready = step != 1 || about.first.isNotBlank()
    BackHandler(step > 0) { step-- }
    Column(Modifier.fillMaxSize().background(p.bg).systemBarsPadding().imePadding()) {
        Progress(step)
        AnimatedContent(
            step,
            Modifier.weight(1f),
            transitionSpec = {
                val fwd = targetState > initialState
                if (on) {
                    (slideInHorizontally(Motion.soft()) { (if (fwd) it else -it) / 5 } + fadeIn(Motion.soft())) togetherWith
                        (slideOutHorizontally(Motion.soft()) { (if (fwd) -it else it) / 5 } + fadeOut(Motion.snappy()))
                } else {
                    fadeIn() togetherWith fadeOut()
                }
            },
            label = "step",
        ) { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 28.dp, bottom = 24.dp)) {
                when (s) {
                    0 -> Welcome()
                    1 -> {
                        Heading("About you", "Just enough to talk to you properly. Only your first name is needed.")
                        Group { AboutFields(about, { about = it }) }
                    }
                    2 -> {
                        Heading(Voice.addr(name, "choose what to read"), "Every source is optional and can be changed later in Settings.")
                        Group { Sources(src::on, { k, v -> src = src.toggled(k, v) }) }
                    }
                    else -> {
                        Heading(Voice.addr(name, "keep it private"), "Ask for your fingerprint or screen lock each time Companion opens. You can turn this on later.")
                        Group { LockRow(src.lock) { src = src.copy(lock = it) } }
                    }
                }
                IconRow(Ic.Shield, "Stays on this phone, encrypted", Modifier.padding(start = 28.dp, top = 8.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, bottom = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) TextBtn("Back") { step-- } else Box(Modifier.size(48.dp))
            Btn(
                if (step == LAST) "Open Companion" else "Continue",
                Modifier.weight(1f).padding(start = 12.dp).height(52.dp),
                go = true,
                enabled = ready,
            ) {
                if (ready && step < LAST) {
                    step++
                } else if (ready) {
                    scope.launch {
                        repo.edit { it.withAbout(about).copy(sms = src.sms, notif = src.notif, mail = src.mail, wa = src.wa, ig = src.ig, lock = src.lock, done = true) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Progress(step: Int) {
    val p = pal
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).semantics { contentDescription = "Step ${step + 1} of ${LAST + 1}" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(LAST + 1) { i ->
            val f by animateFloatAsState(if (i <= step) 1f else 0f, Motion.soft(), label = "seg")
            Box(
                Modifier.weight(1f).height(4.dp).drawBehind {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(p.line, cornerRadius = r)
                    drawRoundRect(p.accent, size = Size(size.width * f, size.height), cornerRadius = r)
                },
            )
        }
    }
}

@Composable
private fun Heading(title: String, body: String) {
    val p = pal
    Text(title, Modifier.padding(horizontal = 24.dp).semantics { heading() }, style = Ty.ui(32, FontWeight.Bold).copy(color = p.ink, lineHeight = 38.sp, letterSpacing = (-0.4).sp))
    Text(body, Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 24.dp), style = Ty.ui(15, FontWeight.Normal).copy(color = p.ink2, lineHeight = 22.sp))
}

@Composable
private fun Welcome() {
    val p = pal
    Box(Modifier.padding(start = 24.dp, bottom = 24.dp).size(72.dp).background(p.accent, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Ic.Sparkle, null, Modifier.size(34.dp), tint = p.onAccent)
    }
    Heading("Welcome to Companion", "Your bank and card messages, bills and codes, sorted on this phone. It files what it is sure of and asks about the rest.")
    Group {
        PassLine("Codes at a glance", "Copy a one-time code while it is still valid", lead = Ic.Copy)
        Rule(72.dp)
        PassLine("Bills before they are late", "Dues and statements, with a reminder", lead = Ic.Bills)
        Rule(72.dp)
        PassLine("Spending, filed", "Every rupee by category, merged across sources", lead = Ic.Ledger)
        Rule(72.dp)
        PassLine("Ask in plain words", "\"Food spend this month\" answered on device", lead = Ic.Sparkle)
    }
}
