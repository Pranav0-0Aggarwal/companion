package app.companion.ui.screens

import android.Manifest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.core.Suggest
import app.companion.core.Suggestion
import app.companion.data.Profile
import app.companion.sl
import app.companion.system.CalChoice
import app.companion.system.CalEvent
import app.companion.system.Cals
import app.companion.system.Plan
import app.companion.ui.Ty
import app.companion.ui.Voice
import app.companion.data.cal
import app.companion.ui.clock
import app.companion.ui.dateOf
import app.companion.ui.dayLabel
import app.companion.ui.has
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Group
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Rule
import app.companion.ui.kit.Section
import app.companion.ui.kit.Stamp
import app.companion.ui.kit.Toggle
import app.companion.ui.pal
import app.companion.ui.rememberNow
import app.companion.ui.rememberPerms
import app.companion.ui.zone
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val calPerms = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

@Composable
fun CalBtn(s: Suggestion.Cal, modifier: Modifier = Modifier, onAdded: () -> Unit = {}) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var added by remember { mutableStateOf(false) }
    val add = { scope.launch { added = withContext(Dispatchers.IO) { Cals.add(c, c.sl.repo.profileNow().cal, s) }
        if (added) onAdded() } }
    val ask = rememberPerms(*calPerms) { add() }
    Btn(if (added) "Added" else "Add to calendar", modifier) {
        if (!added) if (c.has(Manifest.permission.WRITE_CALENDAR)) add() else ask()
    }
}

@Composable
fun RemindBtn(title: String, due: LocalDate, modifier: Modifier = Modifier) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var set by remember { mutableStateOf(false) }
    val picks = remember(title, due) { Suggest.bill(title, due, System.currentTimeMillis(), zone()) }
    if (picks.isNotEmpty()) {
        Btn(if (set) "Reminders set" else "Remind me", modifier) {
            if (!set) {
                set = true
                scope.launch { picks.forEach { Plan.remind(c, title, it.at, "bill") } }
            }
        }
    }
}

@Composable
fun NextUp(go: (String) -> Unit) {
    val c = LocalContext.current
    val repo = c.sl.repo
    val p by repo.profile.collectAsStateWithLifecycle(Profile())
    val next by repo.next().collectAsStateWithLifecycle(null)
    val now by rememberNow(30_000)
    var tick by remember { mutableIntStateOf(0) }
    val events by produceState(emptyList<CalEvent>(), tick) { value = withContext(Dispatchers.IO) { Cals.today(c) } }
    val ask = rememberPerms(*calPerms) { tick++ }
    val scope = rememberCoroutineScope()
    Section("Next up", "Plan") { go("plan") }
    Group {
        val n = next
        if (n != null) {
            val mins = ((n.remindAt ?: now) - now) / 60_000
            PassLine(
                title = n.title,
                sub = Voice.addr(p.name, if (mins < 60) "in ${mins.coerceAtLeast(0)} min" else "at ${clock(n.remindAt ?: now)}"),
                trailing = { if (mins < 60) Stamp("SOON") },
                actions = { Btn("Done") { scope.launch { Plan.finish(c, n.id) } } },
            )
            Rule()
        }
        events.forEach { e ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (e.allDay) "all day" else clock(e.start), Modifier.width(64.dp), style = Ty.mono(12).copy(color = pal.ink2))
                Column {
                    Text(e.title, style = Ty.ui(14).copy(color = pal.ink))
                    if (e.place.isNotBlank()) Text(e.place, style = Ty.mono(10).copy(color = pal.ink2))
                }
            }
        }
        if (!c.has(Manifest.permission.READ_CALENDAR)) {
            PassLine("Show today's calendar", "Read only on this phone", actions = { Btn("Allow") { ask() } })
        } else if (n == null && events.isEmpty()) {
            PassLine("Nothing planned", Voice.addr(p.name, "your day is open"))
        }
    }
}

@Composable
fun Suggested() {
    val repo = LocalContext.current.sl.repo
    val items by repo.suggested().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    if (items.isEmpty()) return
    Section("Suggested")
    Group {
        items.forEachIndexed { k, i ->
            val s = i.cal() ?: return@forEachIndexed
            if (k > 0) Rule()
            PassLine(s.title, "${dayLabel(dateOf(s.start))}${if (s.allDay) "" else " ${clock(s.start)}"}", actions = {
                CalBtn(s) { scope.launch { repo.ping(i.id, i.ping or 8) } }
            })
        }
    }
}

@Composable
fun PlanSettings(modifier: Modifier = Modifier, onTasks: (Boolean) -> Unit) {
    val c = LocalContext.current
    val repo = c.sl.repo
    val p by repo.profile.collectAsStateWithLifecycle(Profile())
    var tick by remember { mutableIntStateOf(0) }
    val choices by produceState(emptyList<CalChoice>(), tick) { value = withContext(Dispatchers.IO) { Cals.choices(c) } }
    val ask = rememberPerms(*calPerms) { tick++ }
    val scope = rememberCoroutineScope()
    Column(modifier) {
        Section("Calendar")
        Group {
            if (!c.has(Manifest.permission.READ_CALENDAR)) {
                PassLine("Choose where events go", "Needs calendar access", actions = { Btn("Allow") { ask() } })
            }
            choices.forEach { ch ->
                Row(
                    Modifier.fillMaxWidth().clickable { scope.launch { repo.edit { it.copy(cal = ch.id) } } }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(ch.name, style = Ty.ui(14).copy(color = pal.ink))
                        Text(ch.account, style = Ty.mono(10).copy(color = pal.ink2))
                    }
                    if (p.cal == ch.id) Stamp("USED", ok = true)
                }
            }
        }
        Section("To-dos")
        Group {
            Toggle("Sync with Google Tasks", "Two-way, optional. The token stays in memory only.", p.gtasks) { on ->
                scope.launch { repo.edit { it.copy(gtasks = on) } }
                onTasks(on)
            }
        }
    }
}
