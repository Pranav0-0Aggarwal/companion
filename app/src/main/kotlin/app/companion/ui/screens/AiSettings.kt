package app.companion.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import app.companion.ai.Have
import app.companion.ai.Manifest
import app.companion.ai.ModelJobs
import app.companion.ai.ModelWork
import app.companion.ai.Models
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Group
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Rule
import app.companion.ui.kit.Section
import app.companion.ui.pal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AiSettings() {
    val c = LocalContext.current
    val gov = c.sl.gov
    val live by gov.live.collectAsStateWithLifecycle()
    val job by remember { ModelJobs.watch(c) }.collectAsStateWithLifecycle(null)
    val state = job?.state
    val have by produceState(emptyMap<String, Have>(), state) {
        value = withContext(Dispatchers.IO) { Manifest.all.associate { it.file to Models.have(c, it) } }
    }
    val busy = state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.BLOCKED
    val missing = Manifest.all.any { Models.pinned(it) && (have[it.file] ?: Have.No) == Have.No }
    Section("On-device AI")
    Group {
        Manifest.all.forEach { s ->
            val h = have[s.file] ?: Have.No
            val text = when {
                !Models.pinned(s) -> "waiting for the pinned checksum"
                h == Have.Custom -> "custom"
                h == Have.Base -> "ready"
                state == WorkInfo.State.RUNNING && job?.progress?.getString(ModelWork.FILE) == s.file -> "downloading ${job?.progress?.getInt(ModelWork.PCT, 0)}%"
                state == WorkInfo.State.RUNNING -> "queued"
                busy -> "waiting for Wi-Fi"
                else -> "not installed"
            }
            val on = live?.name == s.name
            PassLine(s.name, if (on) "$text · loaded · ${live?.accel} · about ${live?.mb} MB" else text)
            Rule()
        }
        PassLine("Memory", "Process about ${gov.rssMb()} MB. One model at a time. Decide unloads after 30 s idle, Needle after 60 s.")
        Rule()
        Column(Modifier.padding(16.dp)) {
            if (busy) {
                Btn("Cancel download") { ModelJobs.stop(c) }
            } else if (missing) {
                Btn("Download models (≈520 MB, Wi-Fi)", go = true) { ModelJobs.start(c) }
            }
            if (state == WorkInfo.State.FAILED) {
                Text("Download failed: size or checksum does not match", Modifier.padding(top = 8.dp), style = Ty.mono(11).copy(color = pal.ink2))
            }
            Text("Private models with adb", Modifier.padding(top = 12.dp), style = Ty.ui(13).copy(color = pal.ink))
            Text(
                "custom.json: {\"decide.tflite\": \"<sha256>\"}\nadb push decide.tflite custom.json /data/local/tmp/\nadb shell run-as app.companion sh -c 'mkdir -p files/models/custom && cp /data/local/tmp/decide.tflite /data/local/tmp/custom.json files/models/custom/'",
                Modifier.padding(top = 6.dp),
                style = Ty.mono(10).copy(color = pal.ink2),
            )
        }
    }
}
