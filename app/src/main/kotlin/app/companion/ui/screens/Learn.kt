package app.companion.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.ai.Models
import app.companion.core.Rules
import app.companion.data.RuleRow
import app.companion.sl
import app.companion.system.Export
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

private fun RuleRow.sub() = listOf(task, "→ $label", if (count >= Rules.MIN) "auto-files" else "$count of ${Rules.MIN}").joinToString(" · ")

@Composable
fun LearnSettings() {
    val c = LocalContext.current
    val repo = c.sl.repo
    val scope = rememberCoroutineScope()
    var since by remember { mutableLongStateOf(Export.last(c)) }
    var note by remember { mutableStateOf<String?>(null) }
    val n by remember(since) { repo.corrections(since) }.collectAsStateWithLifecycle(0)
    val rules by repo.rules.collectAsStateWithLifecycle(emptyList())
    val share = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { Export.discard(c) }
    val decide = remember { Models.version(c, Models.decide) }
    val cal = remember { Models.version(c, Models.calibration) }
    Section("Corrections")
    Group {
        PassLine("Since last export", "$n ${if (n == 1) "correction" else "corrections"}")
        Rule()
        PassLine("Decide model", listOfNotNull(decide?.let { "version $it" } ?: "not installed", cal?.let { "calibration $it" }).joinToString(" · "))
        Rule()
        PassLine(
            "Export corrections",
            "Opens the share sheet. Nothing is sent automatically.",
            actions = {
                Btn("Export", go = true) {
                    scope.launch {
                        val f = withContext(Dispatchers.IO) { Export.write(c, repo) }
                        if (f == null) {
                            note = "No corrections to export yet"
                        } else {
                            note = null
                            since = System.currentTimeMillis().also { Export.mark(c, it) }
                            share.launch(Export.chooser(c, f))
                        }
                    }
                }
            },
        )
        note?.let { Text(it, Modifier.padding(start = 16.dp, bottom = 12.dp), style = Ty.mono(11).copy(color = pal.ink2)) }
    }
    Section("Learned rules")
    Group {
        if (rules.isEmpty()) PassLine("None yet", "A rule applies after two matching corrections")
        rules.forEachIndexed { i, r ->
            if (i > 0) Rule()
            PassLine(r.title ?: r.hash.take(8), r.sub(), actions = { Btn("Delete") { scope.launch { repo.deleteRule(r.hash, r.task) } } })
        }
    }
}
