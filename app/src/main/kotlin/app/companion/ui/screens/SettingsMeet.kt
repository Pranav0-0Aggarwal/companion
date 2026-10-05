package app.companion.ui.screens

import android.Manifest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import app.companion.core.MeetOpts
import app.companion.core.Meets
import app.companion.system.CalChoice
import app.companion.system.Cals
import app.companion.system.Live
import app.companion.system.Meetings
import app.companion.system.Prefs
import app.companion.ui.Ty
import app.companion.ui.has
import app.companion.ui.kit.Chip
import app.companion.ui.kit.Group
import app.companion.ui.kit.Ic
import app.companion.ui.kit.Rule
import app.companion.ui.kit.Section
import app.companion.ui.kit.Stamp
import app.companion.ui.kit.Toggle
import app.companion.ui.pal
import app.companion.ui.rememberPerms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MeetSettings() {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var o by remember { mutableStateOf(Prefs.meets(c)) }
    var tick by remember { mutableIntStateOf(0) }
    val cals by produceState(emptyList<CalChoice>(), tick, o.on) { value = withContext(Dispatchers.IO) { Cals.visible(c) } }
    val set: (MeetOpts) -> Unit = { n ->
        o = n
        Prefs.setMeets(c, n)
        scope.launch {
            Meetings.sync(c)
            Live.widgets(c)
        }
    }
    val ask = rememberPerms(Manifest.permission.READ_CALENDAR) { ok ->
        tick++
        if (ok) set(o.copy(on = true))
    }
    val shown = if (o.cals.isEmpty()) cals.map { it.id }.toSet() else o.cals
    Column {
        Section("Meetings")
        Group {
            Toggle("Meeting reminders", "A heads-up before calls and meetings, with Join and Directions", o.on, icon = Ic.Calendar) { v ->
                if (!v) set(o.copy(on = false)) else if (c.has(Manifest.permission.READ_CALENDAR)) set(o.copy(on = true)) else ask()
            }
            if (o.on) {
                Rule(72.dp)
                Leads("Video calls", "Heads-up before a call with a link", o.video) { set(o.copy(video = it)) }
                Rule(72.dp)
                Leads("In person", "Heads-up before a meeting at a place", o.place) { set(o.copy(place = it)) }
                Rule(72.dp)
                Toggle("Only meetings with a link or location", "Skips events with neither", o.only, icon = Ic.Check) { set(o.copy(only = it)) }
                if (cals.isNotEmpty()) {
                    Rule(72.dp)
                    Text("Calendars", Modifier.padding(start = 18.dp, top = 14.dp), style = Ty.ui(16, FontWeight.Medium).copy(color = pal.ink))
                    cals.forEach { ch ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                val n = if (ch.id in shown) shown - ch.id else shown + ch.id
                                if (n.isNotEmpty()) set(o.copy(cals = if (n.size == cals.size) emptySet() else n))
                            }.padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(ch.name, style = Ty.ui(16, FontWeight.Medium).copy(color = pal.ink))
                                Text(ch.account, style = Ty.ui(13, FontWeight.Normal).copy(color = pal.ink2))
                            }
                            if (ch.id in shown) Stamp("ON", ok = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Leads(label: String, sub: String, mins: Int, set: (Int) -> Unit) {
    val p = pal
    Column(Modifier.padding(18.dp)) {
        Text(label, style = Ty.ui(16, FontWeight.Medium).copy(color = p.ink))
        Text(sub, Modifier.padding(top = 2.dp), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2))
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Meets.leads.forEach { Chip("$it min", it == mins) { set(it) } }
        }
    }
}
