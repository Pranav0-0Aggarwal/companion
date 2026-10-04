package app.companion.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.core.Source
import app.companion.ingest.SmsImport
import app.companion.data.Profile
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Group
import app.companion.ui.kit.Ic
import app.companion.ui.kit.Screen
import app.companion.ui.kit.Section
import app.companion.ui.kit.ToolButton
import app.companion.ui.pal
import app.companion.ui.rememberTasksLink
import kotlinx.coroutines.launch

private val reads = listOf(
    "Read only access (gmail.readonly). Companion cannot send, delete or change mail.",
    "Only finance and logistics mail: statements, receipts, bills, orders, delivery and travel.",
    "The sign-in token is held in memory only.",
    "Nothing leaves the phone.",
)

@Composable
fun SettingsScreen(back: () -> Unit) {
    val c = LocalContext.current
    val repo = c.sl.repo
    val scope = rememberCoroutineScope()
    val profile by repo.profile.collectAsStateWithLifecycle<Profile?>(null)
    val tasks = rememberTasksLink {}
    var wipe by remember { mutableStateOf(false) }
    val save: ((Profile) -> Profile) -> Unit = { f -> scope.launch { repo.edit(f) } }
    Screen("Settings", "Everything stays on this phone", tools = { ToolButton(Ic.Back, "Back", back) }) {
        profile?.let { p ->
            item(key = "profile") { Section("Profile") }
            item(key = "profile-form") { ProfileForm(p) { a -> save { it.withAbout(a) } } }
            item(key = "sources") { Section("Sources") }
            item(key = "sources-list") {
                Group {
                    Sources(p::on, { s, v ->
                        save { it.toggled(s, v) }
                        if (s == Source.Sms && v && !p.imported) SmsImport.enqueue(c)
                    }, sync = true)
                }
            }
            item(key = "vip") { Section("Important contacts") }
            item(key = "vip-form") { VipForm(p) { v -> save { it.copy(vip = v) } } }
            item(key = "reads") { Section("What Gmail reads") }
            item(key = "reads-list") { Group { Reads() } }
            item(key = "plan") { PlanSettings(onTasks = { if (it) tasks() }) }
            item(key = "ai") { AiSettings() }
            item(key = "learn") { LearnSettings() }
            item(key = "lock") { Section("Lock") }
            item(key = "lock-row") { Group { LockRow(p.lock) { v -> save { it.copy(lock = v) } } } }
            item(key = "wipe") { Section("Data") }
            item(key = "wipe-btn") { Btn("Wipe all data", Modifier.padding(horizontal = 20.dp), go = true) { wipe = true } }
        }
    }
    if (wipe) {
        PassConfirm("Wipe all data?", "Every item, card and setting on this phone is deleted. This can't be undone.", "Wipe", { c.sl.wipe() }, { wipe = false })
    }
}

@Composable
private fun Reads() {
    val p = pal
    Column(Modifier.padding(16.dp)) {
        reads.forEachIndexed { i, line ->
            Text(line, Modifier.padding(top = if (i == 0) 0.dp else 8.dp), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2))
        }
    }
}
