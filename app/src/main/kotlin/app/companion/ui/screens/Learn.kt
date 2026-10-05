package app.companion.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.ai.Active
import app.companion.ai.Manifest
import app.companion.ai.Models
import app.companion.core.Senders
import app.companion.core.Template
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

private val space = Regex("\\s+")

private fun RuleRow.line(): String {
    val brand = sender?.let { Template.brand(it, body.orEmpty()) } ?: title.orEmpty()
    val text = Template.mask(body ?: note?.takeIf { it.isNotBlank() } ?: title.orEmpty()).replace(space, " ").trim()
    val eg = if (text.length > 60) text.take(59).trimEnd() + "…" else text
    return listOf(brand, eg).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "Messages like this" }
}

@Composable
fun LearnSettings() {
    val c = LocalContext.current
    val repo = c.sl.repo
    val scope = rememberCoroutineScope()
    var since by remember { mutableLongStateOf(Export.last(c)) }
    var note by remember { mutableStateOf<String?>(null) }
    val n by remember(since) { repo.corrections(since) }.collectAsStateWithLifecycle(0)
    val rules by repo.rules.collectAsStateWithLifecycle(emptyList())
    val merchants by repo.learned.collectAsStateWithLifecycle(emptyList())
    val senders by repo.senderRules.collectAsStateWithLifecycle(emptyList())
    val share = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { Export.discard(c) }
    val ver by produceState<Pair<String?, String?>>(null to null) {
        value = withContext(Dispatchers.IO) { Active.version(c) to Models.version(c, Manifest.calibration) }
    }
    val (decide, cal) = ver
    Section("Corrections")
    Group {
        PassLine("Since last export", "$n ${if (n == 1) "correction" else "corrections"}")
        Rule()
        PassLine("Message classifier", listOfNotNull(decide?.let { "version $it" } ?: "not installed", cal?.let { "calibration $it" }).joinToString(" · "))
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
        note?.let { Text(it, Modifier.padding(start = 16.dp, bottom = 12.dp), style = Ty.ui(13, androidx.compose.ui.text.font.FontWeight.Normal).copy(color = pal.ink2)) }
    }
    Section("Rules")
    Group {
        if (rules.isEmpty()) PassLine("None yet", "Correct a message once and it is filed the same way next time")
        rules.forEachIndexed { i, r ->
            if (i > 0) Rule()
            PassLine(
                r.line(),
                "→ ${r.label.cap()} · caught ${r.hits}",
                actions = { Btn("Delete") { scope.launch { repo.deleteRule(r.hash, r.task) } } },
            )
        }
    }
    Section("Merchant categories")
    Group {
        if (merchants.isEmpty()) PassLine("None yet", "Filing a payment under a category remembers its merchant")
        merchants.forEachIndexed { i, m ->
            if (i > 0) Rule()
            PassLine(m.key.cap(), "→ ${m.category.cap()}", actions = { Btn("Delete") { scope.launch { repo.deleteLearned(m.key) } } })
        }
    }
    Section("Sender rules")
    Group {
        if (senders.isEmpty()) PassLine("None yet", "A sender is remembered after three matching corrections")
        senders.forEachIndexed { i, r ->
            if (i > 0) Rule()
            PassLine(Senders.name(r.sender), "Always ${r.label}", actions = { Btn("Delete") { scope.launch { repo.deleteSender(r.sender) } } })
        }
    }
}
