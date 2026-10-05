package app.companion.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.companion.ui.rememberGmailLink
import android.Manifest
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.companion.BuildConfig
import app.companion.core.Source
import app.companion.data.Profile
import app.companion.ui.Ty
import app.companion.ui.has
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Ic
import app.companion.ui.kit.Rule
import app.companion.ui.kit.Toggle
import app.companion.ui.listenerOn
import app.companion.ui.openListenerSettings
import app.companion.ui.pal
import app.companion.ui.rememberPerms

fun Profile.toggled(s: Source, v: Boolean) = when (s) {
    Source.Sms -> copy(sms = v)
    Source.Notif -> copy(notif = v)
    Source.Mail -> copy(mail = v)
    Source.Wa -> copy(wa = v)
    Source.Ig -> copy(ig = v)
}

@Composable
private fun rememberListenerOn(): Boolean {
    val c = LocalContext.current
    var on by remember { mutableStateOf(c.listenerOn()) }
    LifecycleResumeEffect(Unit) {
        on = c.listenerOn()
        onPauseOrDispose {}
    }
    return on
}

@Composable
fun Sources(on: (Source) -> Boolean, set: (Source, Boolean) -> Unit, sync: Boolean = false) {
    val c = LocalContext.current
    val listener = rememberListenerOn()
    val askSms = rememberPerms(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS) { ok -> if (ok) set(Source.Sms, true) }
    val askNotif = rememberPerms(Manifest.permission.POST_NOTIFICATIONS) {
        set(Source.Notif, true)
        if (!c.listenerOn()) c.openListenerSettings()
    }
    val chat = { s: Source, v: Boolean ->
        set(s, v)
        if (v && !c.listenerOn()) c.openListenerSettings()
    }
    Toggle("SMS", "Bank, card and bill messages, past and new", on(Source.Sms), icon = Ic.Sms) { v ->
        if (!v) set(Source.Sms, false) else if (c.has(Manifest.permission.RECEIVE_SMS) && c.has(Manifest.permission.READ_SMS)) set(Source.Sms, true) else askSms()
    }
    Rule(72.dp)
    Toggle("Notifications", if (listener) "Listening to app alerts" else "Needs notification access", on(Source.Notif), icon = Ic.Bell) { v ->
        if (v) askNotif() else set(Source.Notif, false)
    }
    if (!listener) Btn("Open access settings", Modifier.padding(start = 72.dp, bottom = 12.dp)) { c.openListenerSettings() }
    Rule(72.dp)
    GmailSource(on(Source.Mail), { set(Source.Mail, it) }, sync)
    Rule(72.dp)
    Toggle("WhatsApp", "Chats that need you, the rest wait quietly in Inbox", on(Source.Wa), icon = Ic.Chat) { chat(Source.Wa, it) }
    Rule(72.dp)
    Toggle("Instagram", "Chats that need you, the rest wait quietly in Inbox", on(Source.Ig), icon = Ic.Camera) { chat(Source.Ig, it) }
}

@Composable
private fun GmailSource(on: Boolean, set: (Boolean) -> Unit, sync: Boolean) {
    if (BuildConfig.GMAIL_CLIENT_ID.isBlank()) {
        val p = pal
        Column(Modifier.padding(18.dp)) {
            Text("Set up Gmail", style = Ty.ui(16, FontWeight.Medium).copy(color = p.ink))
            Text(
                "Create an OAuth client in Google Cloud and add your account as a test user. Put the client id in local.properties as gmail.webClientId, then rebuild.",
                Modifier.padding(top = 2.dp),
                style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2),
            )
        }
    } else {
        var failed by remember { mutableStateOf(false) }
        val link = rememberGmailLink { ok ->
            failed = !ok
            if (ok) set(true)
        }
        Toggle("Gmail", if (failed) "Couldn't connect. Try again." else "Statements, receipts and bills, read only", on, icon = Ic.Mail) { v -> if (v) link() else set(false) }
        if (on && sync) Btn("Sync now", Modifier.padding(start = 72.dp, bottom = 12.dp), onClick = link)
    }
}
