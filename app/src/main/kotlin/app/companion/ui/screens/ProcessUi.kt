package app.companion.ui.screens

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.ai.Job
import app.companion.ai.Processing
import app.companion.ai.Run
import app.companion.core.Show
import app.companion.data.Profile
import app.companion.sl
import app.companion.ui.Fin
import app.companion.ui.Proc
import app.companion.ui.Ty
import app.companion.ui.has
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Group
import app.companion.ui.kit.Ic
import app.companion.ui.kit.Motion
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Rule
import app.companion.ui.kit.Section
import app.companion.ui.kit.TextBtn
import app.companion.ui.kit.Toggle
import app.companion.ui.kit.Tone
import app.companion.ui.kit.motion
import app.companion.ui.pal
import app.companion.ui.rememberBanner
import app.companion.ui.rememberCharge
import app.companion.ui.rememberStart
import java.text.NumberFormat

private val nf = NumberFormat.getIntegerInstance()

private fun Int.n(): String = nf.format(this)

private class Hold<T> {
    var v: T? = null
}

@Composable
private fun <T : Any> Reveal(v: T?, content: @Composable (T) -> Unit) {
    val on = motion()
    val hold = remember { Hold<T>() }
    if (v != null) hold.v = v
    AnimatedVisibility(
        v != null,
        enter = if (on) expandVertically(Motion.soft()) + fadeIn(Motion.soft()) else EnterTransition.None,
        exit = if (on) shrinkVertically(Motion.soft()) + fadeOut(Motion.soft()) else ExitTransition.None,
    ) { (v ?: hold.v)?.let { content(it) } }
}

@Composable
private fun rememberCount(job: Job, idle: Boolean, imported: Boolean, sms: Boolean): State<Int?> {
    val c = LocalContext.current
    val changed by Processing.modelChanged.collectAsStateWithLifecycle()
    return produceState<Int?>(null, job, idle, imported, sms, changed) { if (idle) value = Processing.count(c, job) }
}

@Composable
private fun Charge() {
    val (on, set) = rememberCharge()
    Toggle("Only while charging (keeps the phone cool)", "", on, icon = Ic.Bolt, onChange = set)
    Reveal(Unit.takeIf { !on }) {
        Text(
            "On battery this uses more power and can make the phone warm.",
            Modifier.padding(start = 72.dp, end = 18.dp, bottom = 12.dp),
            style = Ty.ui(13, FontWeight.Normal).copy(color = pal.ink2),
        )
    }
}

@Composable
private fun Bar(f: Float, state: String) {
    val p = pal
    val k by animateFloatAsState(f, if (motion()) Motion.soft() else snap(), label = "bar")
    Box(
        Modifier.fillMaxWidth().height(6.dp).semantics {
            stateDescription = state
            progressBarRangeInfo = ProgressBarRangeInfo(f, 0f..1f)
        }.drawBehind {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(p.line, cornerRadius = r)
            drawRoundRect(p.accent, size = Size(size.width * k.coerceIn(0f, 1f), size.height), cornerRadius = r)
        },
    )
}

@Composable
private fun RunBody(r: Run) {
    val c = LocalContext.current
    val p = pal
    val title = if (r.job == Job.Import) "Importing message history" else "Reprocessing everything"
    val user = r.paused && r.reason == null
    val status = when {
        r.reason != null -> r.reason
        user -> "Paused"
        r.etaSec == null -> "estimating time"
        r.etaSec < 60 -> Show.eta(r.etaSec)
        else -> "about ${Show.eta(r.etaSec)}"
    }
    val line = "${r.done.n()} of ${r.total.n()} · $status"
    Column(Modifier.padding(18.dp)) {
        Text(title, style = Ty.ui(16, FontWeight.Medium).copy(color = p.ink))
        Box(Modifier.padding(top = 12.dp)) { Bar(if (r.total > 0) r.done.toFloat() / r.total else 0f, line) }
        Text(line, Modifier.padding(top = 8.dp).clearAndSetSemantics {}, style = Ty.mono(13, FontWeight.Normal).copy(color = p.ink2))
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (user) Btn("Resume", go = true) { Processing.resume(c) } else Btn("Pause") { Processing.pause(c) }
            Btn("Cancel") { Processing.cancel(c) }
        }
    }
}

@Composable
private fun Banner(start: () -> Unit, later: () -> Unit) {
    PassLine(
        "A new model is ready. Reprocess your messages to use it.", "", lead = Ic.Swap, lines = 3,
        actions = {
            Btn("Reprocess", go = true, dense = true, onClick = start)
            TextBtn("Later", onClick = later)
        },
    )
}

@Composable
private fun Result(f: Fin) {
    val c = LocalContext.current
    val imp = f.job == Job.Import
    PassLine(
        if (imp) "Import finished" else "Reprocessing finished",
        if (imp) "${f.n.n()} messages read" else "${f.n.n()} messages checked. Corrections and learned rules were kept.",
        lead = Ic.Check, tone = Tone.Green, lines = 3,
        actions = { TextBtn("Dismiss") { Proc.dismiss(c) } },
    )
}

@Composable
fun ProcessSlot() {
    val c = LocalContext.current
    val prof by c.sl.repo.profile.collectAsStateWithLifecycle<Profile?>(null)
    val run by Processing.state.collectAsStateWithLifecycle()
    val p = prof ?: return
    val n by rememberCount(Job.Import, run == null, p.imported, p.sms)
    val re by rememberCount(Job.Reprocess, run == null, p.imported, p.sms)
    val first = Proc.first(c, p) && (n ?: 1) > 0
    val (show, later) = rememberBanner(first, re)
    val start = rememberStart()
    Column {
        Reveal(run) { r -> Box(Modifier.padding(bottom = 12.dp)) { Group { RunBody(r) } } }
        Reveal(n.takeIf { first && run == null }) { k ->
            Box(Modifier.padding(bottom = 12.dp)) {
                Group {
                    PassLine("Import your messages to get started", "${k.n()} messages found", lead = Ic.Download, lines = 3)
                    Rule(72.dp)
                    Charge()
                    Btn("Import now", Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 16.dp), go = true) { start(Job.Import) }
                }
            }
        }
        Reveal(Unit.takeIf { show }) { Box(Modifier.padding(bottom = 12.dp)) { Group { Banner({ start(Job.Reprocess) }, later) } } }
    }
}

@Composable
fun ProcessingSection() {
    val c = LocalContext.current
    val prof by c.sl.repo.profile.collectAsStateWithLifecycle<Profile?>(null)
    val run by Processing.state.collectAsStateWithLifecycle()
    val fin by Proc.done.collectAsStateWithLifecycle()
    val p = prof ?: return
    val idle = run == null
    val imp by rememberCount(Job.Import, idle, p.imported, p.sms)
    val re by rememberCount(Job.Reprocess, idle, p.imported, p.sms)
    val i = imp
    val k = re
    val (show, later) = rememberBanner(Proc.first(c, p) && (i ?: 1) > 0, k)
    val start = rememberStart()
    val sub = when {
        i == null -> "Counting"
        i > 0 -> "${i.n()} messages to read"
        !p.sms -> "Turn on SMS in Sources to import"
        !c.has(Manifest.permission.READ_SMS) -> "Allow SMS access in Sources to import"
        p.imported -> "Already imported"
        else -> "No messages to import"
    }
    val resub = when {
        k == null -> "Counting"
        k > 0 -> "${k.n()} SMS and chats. Corrections and learned rules are kept."
        else -> "Nothing to reprocess yet"
    }
    Section("Processing")
    Group {
        Reveal(Unit.takeIf { show }) {
            Column {
                Banner({ start(Job.Reprocess) }, later)
                Rule(72.dp)
            }
        }
        val r = run
        if (r != null) {
            RunBody(r)
        } else {
            fin?.let {
                Result(it)
                Rule(72.dp)
            }
            PassLine(
                "Import message history", sub, lead = Ic.Download, lines = 4,
                actions = { Btn("Import", Modifier.semantics { contentDescription = "Import message history" }, enabled = (i ?: 0) > 0) { start(Job.Import) } },
            )
            Rule(72.dp)
            PassLine(
                "Reprocess everything", resub, lead = Ic.Swap, lines = 4,
                actions = { Btn("Reprocess", Modifier.semantics { contentDescription = "Reprocess everything" }, enabled = (k ?: 0) > 0) { start(Job.Reprocess) } },
            )
            Rule(72.dp)
            Charge()
        }
    }
}
