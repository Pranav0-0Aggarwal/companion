package app.companion.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.data.Profile
import app.companion.ingest.NotifService
import app.companion.sl
import app.companion.ui.kit.Ic
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.TextBtn
import app.companion.ui.kit.Tone
import app.companion.ui.kit.part
import app.companion.ui.listenerOn
import app.companion.ui.openListenerSettings
import app.companion.ui.pal

@Composable
fun Hearing() {
    val c = LocalContext.current
    val p by c.sl.repo.profile.collectAsStateWithLifecycle(Profile())
    var access by remember { mutableStateOf(c.listenerOn()) }
    LifecycleResumeEffect(Unit) {
        access = c.listenerOn()
        onPauseOrDispose {}
    }
    val up by NotifService.up.collectAsStateWithLifecycle()
    if (!(p.wa || p.ig || p.notif) || access && up != false) return
    PassLine(
        "Companion can't see WhatsApp and app notifications",
        "",
        Modifier.part(pal, true, true),
        lead = Ic.Bell,
        tone = Tone.Red,
        onClick = { c.openListenerSettings() },
        trailing = { TextBtn("Turn on") { c.openListenerSettings() } },
    )
}
