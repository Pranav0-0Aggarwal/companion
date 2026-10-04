package app.companion.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.ai.Models
import app.companion.ai.Spec
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Group
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Rule
import app.companion.ui.kit.Section
import app.companion.ui.pal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AiSettings() {
    val c = LocalContext.current
    val gov = c.sl.gov
    val live by gov.live.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf(Models.decide) }
    val scope = rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { c.contentResolver.openInputStream(uri)?.let { Models.install(c, target, it) } == true }
                note = if (ok) "Installed ${target.file}" else "File rejected: checksum does not match"
                tick++
            }
        }
    }
    val row: @Composable (Spec) -> Unit = { s ->
        val on = remember(tick) { Models.installed(c, s) }
        val state = when {
            s.sha.isEmpty() -> "waiting for the pinned checksum"
            !on -> "not installed"
            live?.name == s.name -> "loaded · ${live?.accel} · about ${live?.mb} MB"
            else -> "installed · idle"
        }
        PassLine(s.name, state, actions = { if (s.sha.isNotEmpty()) Btn(if (on) "Replace" else "Pick file") { target = s; pick.launch(arrayOf("*/*")) } })
    }
    Section("On-device AI")
    Group {
        row(Models.decide)
        Rule()
        row(Models.needle)
        Rule()
        PassLine("Memory", "Process about ${gov.rssMb()} MB. One model at a time. Decide unloads after 30 s idle.")
        Rule()
        Column(Modifier.padding(16.dp)) {
            Text("Install with adb", style = Ty.ui(13).copy(color = pal.ink))
            Text(
                "adb push ${Models.decide.file} /data/local/tmp/\nadb shell run-as app.companion sh -c 'mkdir -p files/models && cp /data/local/tmp/${Models.decide.file} files/models/'",
                Modifier.padding(top = 6.dp),
                style = Ty.mono(10).copy(color = pal.ink2),
            )
            note?.let { Text(it, Modifier.padding(top = 8.dp), style = Ty.mono(11).copy(color = pal.settled)) }
        }
    }
}
