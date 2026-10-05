package app.companion.ui.screens

import android.Manifest
import androidx.compose.foundation.background
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import app.companion.ai.Have
import app.companion.ai.Manifest as Pins
import app.companion.ai.Mode
import app.companion.ai.ModelJobs
import app.companion.ai.Models
import app.companion.ai.PrivateImport
import app.companion.core.Show
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.has
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Group
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Rule
import app.companion.ui.kit.Section
import app.companion.ui.kit.Stamp
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
    val imp by PrivateImport.state.collectAsStateWithLifecycle()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { PrivateImport.start(c, it) }
    var ask by remember { mutableStateOf(false) }
    val custom by produceState(false, imp.rev) { value = withContext(Dispatchers.IO) { Models.custom(c).list().orEmpty().isNotEmpty() } }
    val askNotif = rememberPerms(Manifest.permission.POST_NOTIFICATIONS) {}
    LaunchedEffect(Unit) { Dl.sync(c) }
    val disk by produceState(emptyMap<String, Pair<Have, Long>>(), s.mode, s.cur, imp.rev) {
        value = withContext(Dispatchers.IO) {
            Pins.all.associate { f -> f.file to Models.have(c, f).let { h -> h to if (h == Have.No) Dl.partial(c, f) else f.bytes } }
        }
    }
    val kind by produceState("", s.mode, s.cur, imp.rev) { value = withContext(Dispatchers.IO) { Active.label(c) } }
    val run = s.mode == Mode.Running
    val busy = run || s.mode == Mode.Waiting
    val total = remember { Dl.total }
    val got = if (run) s.ready + s.pos else Pins.wanted.filter(Models::pinned).sumOf { disk[it.file]?.second ?: 0 }
    val missing = Pins.wanted.any { Models.pinned(it) && (disk[it.file]?.first ?: Have.No) == Have.No }
    val resume = !busy && got > 0 && missing
    val cur = Pins.all.indexOfFirst { it.file == s.cur }
    Section("On-device AI")
    Group {
        Pins.all.forEachIndexed { i, f ->
            val h = disk[f.file]?.first ?: Have.No
            val part = disk[f.file]?.second ?: 0
            val text = when {
                f in Pins.llama && !Chip.nux -> "Not supported on this phone's CPU"
                !Models.pinned(f) -> "waiting for the pinned checksum"
                h == Have.Custom -> "custom"
                h == Have.Base -> "ready"
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
        PassLine("Message classifier", if (live?.name?.startsWith("ModernBERT") == true) "$kind · loaded · ${live?.accel} · about ${live?.mb} MB" else kind)
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
        Rule()
        PassLine(
            "Private models",
            if (imp.busy) imp.note + if (imp.total > 0) " · ${Show.mb(imp.done)} of ${Show.mb(imp.total)} MB" else "" else "Copy custom.json and the model files to Downloads, then pick them all.",
            actions = {
                Btn("Import private model files", enabled = !imp.busy) { pick.launch(arrayOf("*/*")) }
                if (custom) Btn("Remove private models", enabled = !imp.busy) { ask = true }
            },
        )
        if (imp.busy && imp.total > 0) {
            val frac = (imp.done.toDouble() / imp.total).toFloat().coerceIn(0f, 1f)
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(4.dp).background(pal.line)) { Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(pal.accent)) }
        }
        imp.lines.forEach { l ->
            Rule()
            PassLine(l.name, l.note, trailing = { Stamp(if (l.ok) "OK" else "FAILED", ok = l.ok) })
        }
    }
    if (ask) {
        PassConfirm("Remove private models?", "Deletes the private files from this phone and goes back to the base models. Messages are marked for reprocessing when the classifier changes.", "Remove", { ask = false; PrivateImport.remove(c) }, { ask = false })
    }
}
