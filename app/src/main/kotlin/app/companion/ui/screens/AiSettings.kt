package app.companion.ui.screens

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.ai.Active
import app.companion.ai.Chip
import app.companion.ai.Dl
import app.companion.ai.Manifest as Pins
import app.companion.ai.Mode
import app.companion.ai.ModelJobs
import app.companion.ai.Models
import app.companion.core.Show
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.has
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Group
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Rule
import app.companion.ui.kit.Section
import app.companion.ui.pal
import app.companion.ui.rememberPerms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AiSettings() {
    val c = LocalContext.current
    val gov = c.sl.gov
    val scope = rememberCoroutineScope()
    val live by gov.live.collectAsStateWithLifecycle()
    val s by Dl.state.collectAsStateWithLifecycle()
    val askNotif = rememberPerms(Manifest.permission.POST_NOTIFICATIONS) {}
    LaunchedEffect(Unit) { Dl.sync(c) }
    val disk by produceState(emptyMap<String, Pair<Boolean, Long>>(), s.mode, s.cur) {
        value = withContext(Dispatchers.IO) {
            Pins.all.associate { f -> f.file to Models.has(c, f).let { h -> h to if (h) f.bytes else Dl.partial(c, f) } }
        }
    }
    val bert by produceState(false, s.mode, s.cur) { value = withContext(Dispatchers.IO) { Active.bert(c) != null } }
    val run = s.mode == Mode.Running
    val busy = run || s.mode == Mode.Waiting
    val total = remember { Dl.total }
    val got = if (run) s.ready + s.pos else Pins.wanted(Dl.talk).filter(Models::pinned).sumOf { disk[it.file]?.second ?: 0 }
    val missing = Pins.wanted(Dl.talk).any { Models.pinned(it) && disk[it.file]?.first != true }
    val resume = !busy && got > 0 && missing
    val cur = Pins.all.indexOfFirst { it.file == s.cur }
    Section("On-device AI")
    Group {
        Pins.all.forEachIndexed { i, f ->
            val h = disk[f.file]?.first == true
            val part = disk[f.file]?.second ?: 0
            val text = when {
                f in Pins.llama && !Chip.nux -> "Not supported on this phone's CPU"
                !Models.pinned(f) -> "waiting for the pinned checksum"
                h -> "ready"
                run && cur == i -> if (s.pos >= f.bytes) "verifying" else "downloading ${Show.pct(s.pos, f.bytes)}% · ${Show.mb(s.pos)} of ${Show.mb(f.bytes)} MB"
                run && cur > i -> "ready"
                run -> "queued"
                s.mode == Mode.Waiting -> "waiting for Wi-Fi" + if (part > 0) " · ${Show.pct(part, f.bytes)}%" else ""
                part > 0 -> "paused at ${Show.pct(part, f.bytes)}%"
                else -> "not installed"
            }
            val on = live?.name == f.name
            PassLine(f.name, if (on) "$text · loaded · ${live?.accel} · about ${live?.mb} MB" else text)
            Rule()
        }
        val size = Show.mb(Pins.bert.sumOf { it.bytes })
        val kind = if (bert) "ModernBERT" else "Download the message classifier (≈$size MB, Wi-Fi). Until then messages are filed by rules only."
        PassLine("Message classifier", if (bert && live?.name?.startsWith("ModernBERT") == true) "$kind · loaded · ${live?.accel} · about ${live?.mb} MB" else kind)
        Rule()
        PassLine("Memory", "Process about ${gov.rssMb()} MB. One model at a time. Classifier unloads after 30 s idle, Conversation and Smart extraction after 60 s.")
        Rule()
        Column(Modifier.padding(16.dp)) {
            if (missing || busy) {
                val frac = if (total > 0) (got.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f
                Box(Modifier.fillMaxWidth().height(4.dp).background(pal.line)) { Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(pal.accent)) }
                val line = when {
                    run -> "${Show.mb(got)} of ${Show.mb(total)} MB · ${if (s.speed > 0) Show.speed(s.speed) else "starting"} · ${Show.eta(s.eta)}"
                    s.mode == Mode.Waiting -> "${Show.mb(got)} of ${Show.mb(total)} MB · waiting for unmetered Wi-Fi"
                    resume -> "${Show.mb(got)} of ${Show.mb(total)} MB · paused"
                    else -> "${Show.mb(total)} MB, Wi-Fi only"
                }
                Text(line, Modifier.padding(top = 8.dp), style = Ty.mono(11).copy(color = pal.ink2))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (busy) {
                        Btn("Pause") { ModelJobs.pause(c) }
                    } else {
                        Btn(if (resume) "Resume" else "Download models", go = true) {
                            ModelJobs.start(c)
                            if (!c.has(Manifest.permission.POST_NOTIFICATIONS)) askNotif()
                        }
                    }
                    if (busy || resume) Btn("Cancel") { scope.launch(Dispatchers.IO) { ModelJobs.cancel(c) } }
                }
            }
            if (s.mode == Mode.Failed) {
                Text("Download failed: size or checksum does not match", Modifier.padding(top = 8.dp), style = Ty.mono(11).copy(color = pal.ink2))
            }
            if (s.mode == Mode.Full) {
                Text("Not enough storage: need ${Show.mbUp(s.need)} MB free", Modifier.padding(top = 8.dp), style = Ty.mono(11).copy(color = pal.ink2))
            }
        }
    }
}
