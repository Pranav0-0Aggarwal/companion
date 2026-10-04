package app.companion.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.companion.core.Suggest
import app.companion.core.Suggestion
import app.companion.data.Task
import app.companion.sl
import app.companion.system.Cals
import app.companion.system.Plan
import app.companion.ui.Ty
import app.companion.ui.clock
import app.companion.ui.dateOf
import app.companion.ui.dayLabel
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Field
import app.companion.ui.pal
import app.companion.ui.zone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val LocalCapture = compositionLocalOf<(String) -> Unit> { {} }

private fun whenText(ms: Long) = "${dayLabel(dateOf(ms))} ${clock(ms)}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSheet(initial: String, onDone: () -> Unit) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(initial) }
    val s = remember(text) { text.takeIf { it.isNotBlank() }?.let { Suggest.shared(it, System.currentTimeMillis(), zone()) } }
    val todo = { scope.launch { Plan.save(c, Task(title = text.trim().lineSequence().first().take(120), note = text.trim().takeIf { it.contains('\n') })); onDone() } }
    ModalBottomSheet(onDismissRequest = onDone, containerColor = pal.bg) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp)) {
            Text("New reminder", style = Ty.ui(24, FontWeight.Bold).copy(color = pal.ink))
            Field("What should I remember?", text, { text = it }, Modifier.padding(top = 12.dp))
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (s) {
                    is Suggestion.Cal -> {
                        Btn("Add to calendar · ${whenText(s.start)}", go = true) {
                            scope.launch { withContext(Dispatchers.IO) { Cals.add(c, c.sl.repo.profileNow().cal, s) }; onDone() }
                        }
                        Btn("Just a to-do") { todo() }
                    }
                    is Suggestion.Remind -> {
                        Btn("Remind me · ${whenText(s.at)}", go = true) { scope.launch { Plan.remind(c, s.title, s.at); onDone() } }
                        Btn("Just a to-do") { todo() }
                    }
                    is Suggestion.Todo -> Btn("Save to-do", go = true) { todo() }
                    null -> Unit
                }
            }
        }
    }
}
