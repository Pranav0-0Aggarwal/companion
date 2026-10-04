package app.companion.ui.screens

import app.companion.ingest.SmsImport
import app.companion.ui.rememberGmailLink
import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.companion.data.Profile
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.Voice
import app.companion.ui.has
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Group
import app.companion.ui.kit.Ic
import app.companion.ui.kit.IconRow
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Rule
import app.companion.ui.kit.cloth
import app.companion.ui.pal
import kotlinx.coroutines.launch

private const val LAST = 3

@Composable
fun OnboardingScreen() {
    val p = pal
    val c = LocalContext.current
    val repo = c.sl.repo
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var about by remember { mutableStateOf(AboutYou()) }
    var src by remember { mutableStateOf(Profile(sms = false, notif = false)) }
    val name = (about.call ?: about.first).trim()
    val ready = step != 1 || about.first.isNotBlank()
    BackHandler(step > 0) { step-- }
    Column(Modifier.fillMaxSize().cloth(p).systemBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(LAST + 1) { i ->
                    Spacer(Modifier.weight(1f).height(4.dp).background(if (i <= step) p.foil else p.coverMute.copy(alpha = 0.35f), RoundedCornerShape(2.dp)))
                }
            }
            Text("STEP ${step + 1} OF ${LAST + 1}", style = Ty.mono(11, FontWeight.Bold).copy(color = p.coverMute, letterSpacing = 0.8.sp))
        }
        AnimatedContent(step, Modifier.weight(1f), label = "step") { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 20.dp)) {
                when (s) {
                    0 -> Welcome()
                    1 -> {
                        Heading("About you", "Just enough to talk to you properly. Only the first name is needed.")
                        Group { AboutFields(about) { about = it } }
                    }
                    2 -> {
                        Heading(Voice.addr(name, "choose what to read"), "Every source is optional and can be changed later in Settings.")
                        Group { Sources(src::on, { k, v -> src = src.toggled(k, v) }) }
                    }
                    else -> {
                        Heading(Voice.addr(name, "keep it private"), "Ask for your fingerprint or screen lock each time Companion opens. You can skip this and turn it on later.")
                        Group { LockRow(src.lock) { src = src.copy(lock = it) } }
                    }
                }
            }
        }
        Row(Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 0) Btn("Back") { step-- }
            Spacer(Modifier.weight(1f))
            Btn(if (step == LAST) "Open Companion" else "Continue", Modifier.alpha(if (ready) 1f else 0.4f), go = true) {
                if (ready && step < LAST) {
                    step++
                } else if (ready) {
                    scope.launch {
                        repo.edit { it.withAbout(about).copy(sms = src.sms, notif = src.notif, mail = src.mail, wa = src.wa, ig = src.ig, lock = src.lock, done = true) }
                        if (src.sms && c.has(Manifest.permission.READ_SMS) && c.has(Manifest.permission.RECEIVE_SMS)) SmsImport.enqueue(c)
                    }
                }
            }
        }
    }
}

@Composable
private fun Heading(title: String, body: String) {
    val p = pal
    Text(title, Modifier.padding(bottom = 8.dp), style = Ty.ui(30, FontWeight.ExtraBold).copy(color = p.foil, letterSpacing = (-0.02).sp))
    Text(body, Modifier.padding(bottom = 20.dp), style = Ty.ui(15, FontWeight.Medium).copy(color = p.coverInk))
}

@Composable
private fun Welcome() {
    Heading("Welcome to Companion", "Your bank and card messages, bills and codes, filed into one passbook that prints itself.")
    Group {
        PassLine("Codes at a glance", "Copy a one-time code while it is still valid")
        Rule()
        PassLine("Bills before they are late", "Dues and statements, with the date stamped")
        Rule()
        PassLine("Spending, filed", "Every rupee in a ledger, by category")
    }
    Group(Modifier.padding(top = 12.dp)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) { IconRow(Ic.Shield, "Nothing leaves this phone, and it stays encrypted") }
    }
}
