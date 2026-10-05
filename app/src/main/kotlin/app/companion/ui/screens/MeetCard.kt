package app.companion.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.companion.core.Meets
import app.companion.system.Meetings
import app.companion.system.Soon
import app.companion.ui.hm
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Group
import app.companion.ui.kit.Ic
import app.companion.ui.kit.Ink
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Rule
import app.companion.ui.kit.Stamp
import app.companion.ui.kit.Tone
import app.companion.ui.rememberNow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MeetCard() {
    val c = LocalContext.current
    val now by rememberNow(30_000)
    val soon by produceState(Soon(null, null, emptyList()), now / Meets.MIN) { value = withContext(Dispatchers.IO) { Meetings.soon(c, now) } }
    val next = soon.next
    val clash = soon.clashes.firstOrNull()
    if (next == null && clash == null) return
    Group(Modifier.padding(top = 12.dp)) {
        if (next != null) {
            val mins = Meets.mins(next.start, now)
            PassLine(
                next.title.ifBlank { "Meeting" },
                listOfNotNull(Meets.until(next.start, now), Meets.source(next)).joinToString(" · "),
                lead = Ic.Calendar,
                lines = 1,
                fill = true,
                trailing = { if (mins <= 15) Stamp("SOON", ink = Ink.Amber) else Stamp("NEXT UP", ink = Ink.Quiet) },
                actions = {
                    if (next.join != null) Btn("Join", Modifier.weight(1f), go = true, dense = true) { Meetings.open(c, next) }
                    next.place?.let { p -> Btn("Directions", Modifier.weight(1f), go = next.join == null, dense = true) { Meetings.go(c, Meetings.directions(p)) } }
                },
            )
        }
        if (next != null && clash != null) Rule()
        if (clash != null) PassLine(Meets.clash(clash, ::hm), "${clash.a.title} and ${clash.b.title}", lead = Ic.Clock, tone = Tone.Red, lines = 1)
    }
}
