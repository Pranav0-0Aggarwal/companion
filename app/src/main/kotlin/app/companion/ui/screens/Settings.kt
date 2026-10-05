package app.companion.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.data.Profile
import app.companion.sl
import app.companion.system.Prefs
import app.companion.ui.Recent
import app.companion.ui.Ty
import app.companion.ui.kit.Group
import app.companion.ui.kit.Ic
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Screen
import app.companion.ui.kit.Section
import app.companion.ui.kit.Tone
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
    Screen("Settings", "Everything stays on this phone", back = back, nav = false, tall = false) {
        profile?.let { p ->
            item(key = "profile") { ProfileCard(p) { a -> save { it.withAbout(a) } } }
            item(key = "sources") { Section("Sources") }
            item(key = "sources-list") {
                Group {
                    Sources(p::on, { s, v -> save { it.toggled(s, v) } }, sync = true)
                }
            }
            item(key = "vip") { Section("Important contacts") }
            item(key = "vip-form") { VipForm(p) { v -> save { it.copy(vip = v) } } }
            item(key = "reads") { Section("What Gmail reads") }
            item(key = "reads-list") { Group { Reads() } }
            item(key = "notify") { NotifySettings() }
            item(key = "plan") { PlanSettings(onTasks = { if (it) tasks() }) }
            item(key = "ai") { AiSettings() }
            item(key = "proc") { ProcessingSection() }
            item(key = "learn") { LearnSettings() }
            item(key = "privacy") { Section("Privacy") }
            item(key = "privacy-row") {
                Group {
                    KeepRow(p.keep) { d -> scope.launch { repo.keep(d) } }
                    LockRow(p.lock) { v -> save { it.copy(lock = v) } }
                }
            }
            item(key = "wipe") { Section("Data") }
            item(key = "wipe-row") {
                Group { PassLine("Wipe all data", "Deletes every item, card and setting on this phone", lead = Ic.Trash, tone = Tone.Red, onClick = { wipe = true }) }
            }
        }
    }
    if (wipe) {
        PassConfirm(
            "Wipe all data?",
            "Every item, card and setting on this phone is deleted. This can't be undone.",
            "Wipe",
            {
                Recent.clear(c)
                Prefs.clear(c)
                c.sl.wipe()
            },
            { wipe = false },
        )
    }
}

@Composable
private fun Reads() {
    val p = pal
    Column(Modifier.padding(18.dp)) {
        reads.forEachIndexed { i, line ->
            Text(line, Modifier.padding(top = if (i == 0) 0.dp else 8.dp), style = Ty.ui(14, FontWeight.Normal).copy(color = p.ink2))
        }
    }
}
