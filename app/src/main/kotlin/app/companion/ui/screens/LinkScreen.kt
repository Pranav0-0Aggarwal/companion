package app.companion.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.link.Stage
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Empty
import app.companion.ui.kit.Group
import app.companion.ui.kit.Ic
import app.companion.ui.kit.LocalSnack
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Rule
import app.companion.ui.kit.Screen
import app.companion.ui.kit.Section
import app.companion.ui.kit.TextBtn
import app.companion.ui.kit.Tone
import app.companion.ui.kit.Toggle
import app.companion.ui.has
import app.companion.ui.listenerOn
import app.companion.ui.openListenerSettings
import app.companion.ui.pal
import app.companion.ui.rememberPerms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val perms = arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)

private class Row(val key: String, val label: String, val sub: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val rows = listOf(
    Row("otp", "Codes", "One time codes, as they arrive", Ic.Lock),
    Row("money", "Spending and bills", "Today's spend against budget, ledger, bills and search", Ic.Ledger),
    Row("alerts", "Needs you", "Odd charges, bills due soon and messages worth a look", Ic.Bell),
    Row("battery", "Battery", "Level and charging", Ic.Bolt),
    Row("tasks", "Tasks and meetings", "Sync your tasks and meetings, add tasks from your Mac", Ic.Plan),
    Row("notifs", "App notifications", "Only from the apps you choose", Ic.Inbox),
    Row("clipOut", "Clipboard to Mac", "Text you copy while Companion is open", Ic.Copy),
    Row("clipIn", "Clipboard from Mac", "Lets your Mac set what is on your clipboard", Ic.Copy),
    Row("ask", "Ask Companion", "Your Mac can ask questions, answered on this phone", Ic.Sparkle),
)

private fun Stage.sub(name: String?) = when (this) {
    Stage.Off -> "Off"
    Stage.Unpaired -> "Not paired"
    Stage.NoPermission -> "Allow Bluetooth to connect"
    Stage.BluetoothOff -> "Bluetooth is off"
    Stage.Waiting -> "Waiting for $name"
    Stage.Linking -> "Connecting to $name"
    Stage.Connected -> "Connected to $name"
}

@Composable
fun LinkRow(onClick: () -> Unit) {
    val s by LocalContext.current.sl.link.state.collectAsStateWithLifecycle()
    Group { PassLine("Mac link", s.stage.sub(s.name), lead = Ic.Link, onClick = onClick) }
}

@Composable
fun LinkScreen(back: () -> Unit) {
    val c = LocalContext.current
    val hub = c.sl.link
    val s by hub.state.collectAsStateWithLifecycle()
    val snack = LocalSnack.current
    var unpair by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf(false) }
    var listener by remember { mutableStateOf(c.listenerOn()) }
    LifecycleResumeEffect(Unit) {
        listener = c.listenerOn()
        hub.refresh()
        onPauseOrDispose {}
    }
    val ask = rememberPerms(*perms) { ok -> if (ok) hub.enable(true) else snack.say("Companion needs Bluetooth permission for the link") }
    Screen("Mac link", "Nothing leaves your phone except to your paired Mac, end-to-end encrypted", back = back, nav = false, tall = false) {
        if (s.stage == Stage.Unpaired) {
            item(key = "empty") { Empty(Ic.Link, "Link your Mac", "In Notchy on your Mac, choose Pair phone. Scan the code with your phone camera and Companion opens to confirm.") }
        } else {
            item(key = "status") {
                Group {
                    PassLine(s.name ?: "Mac", s.stage.sub(s.name), lead = Ic.Link, tone = if (s.stage == Stage.Connected) Tone.Green else Tone.Accent)
                    Rule(72.dp)
                    Toggle("Link on", "Advertises over Bluetooth only while no Mac is connected", s.stage != Stage.Off, icon = Ic.Bolt) { if (!it) hub.enable(false) else ask() }
                    if (s.stage == Stage.NoPermission) {
                        Btn("Allow Bluetooth", Modifier.padding(18.dp), go = true) { ask() }
                    }
                    if (!listener && s.stage != Stage.Off) {
                        Rule(72.dp)
                        PassLine("Keep the link alive", "Allow notification access so Android keeps Companion running in the background", lead = Ic.Bell, onClick = { c.openListenerSettings() })
                    }
                }
            }
            item(key = "share") { Section("Share with your Mac") }
            item(key = "share-list") {
                Group {
                    rows.forEachIndexed { n, r ->
                        if (n > 0) Rule(72.dp)
                        Toggle(r.label, r.sub, r.key in s.shares, icon = r.icon) { hub.share(r.key, it) }
                    }
                    if ("notifs" in s.shares) {
                        Rule(72.dp)
                        PassLine("Apps", if (s.apps.isEmpty()) "None chosen" else "${s.apps.size} chosen", lead = Ic.Inbox, onClick = { apps = true })
                    }
                }
            }
            item(key = "privacy") { Section("Privacy") }
            item(key = "privacy-text") {
                Group {
                    Column(Modifier.padding(18.dp)) {
                        listOf(
                            "Bluetooth only. Companion adds no internet access for the link.",
                            "Everything is encrypted between this phone and your Mac. Keys never leave the two devices.",
                            "Everything above is off until you turn it on. Ringing this phone from your Mac is always allowed.",
                        ).forEachIndexed { i, line ->
                            Text(line, Modifier.padding(top = if (i == 0) 0.dp else 8.dp), style = Ty.ui(14, FontWeight.Normal).copy(color = pal.ink2))
                        }
                    }
                }
            }
            item(key = "mac") { Section("Mac") }
            item(key = "unpair") {
                Group { PassLine("Unpair", "Forget ${s.name} and stop advertising", lead = Ic.Trash, tone = Tone.Red, onClick = { unpair = true }) }
            }
        }
    }
    if (unpair) PassConfirm("Unpair ${s.name}?", "Your Mac will stop receiving anything from this phone. Pair again from Notchy any time.", "Unpair", { hub.unpair(); unpair = false }, { unpair = false })
    if (apps) AppsSheet(s.apps, hub::allow) { apps = false }
}

@Composable
private fun AppsSheet(chosen: Set<String>, pick: (String, Boolean) -> Unit, onClose: () -> Unit) {
    val c = LocalContext.current
    val list by produceState<List<Pair<String, String>>?>(null) {
        value = withContext(Dispatchers.Default) {
            val pm = c.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.ResolveInfoFlags.of(0))
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .filter { it.first != c.packageName }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
        }
    }
    Sheet(onClose) {
        SheetTitle("Apps to share", "Notifications from these apps go to your Mac")
        list?.forEach { (pkg, name) -> Toggle(name, "", pkg in chosen) { pick(pkg, it) } }
    }
}

@Composable
fun LinkPairHost() {
    val c = LocalContext.current
    val hub = c.sl.link
    val req by hub.pending.collectAsStateWithLifecycle()
    val snack = LocalSnack.current
    val done = { ok: Boolean ->
        hub.pending.value?.let { if (ok) { hub.pair(it); snack.say("Linked with ${it.name}") } else snack.say("Companion needs Bluetooth permission for the link") }
        hub.pending.value = null
    }
    val ask = rememberPerms(*perms, onResult = done)
    req?.let { r ->
        val p = pal
        AlertDialog(
            onDismissRequest = { hub.pending.value = null },
            containerColor = p.card,
            title = { Text("Link with ${r.name}?", style = Ty.ui(20, FontWeight.Bold).copy(color = p.ink)) },
            text = {
                Column {
                    Text("Check that your Mac shows this code.", style = Ty.ui(15, FontWeight.Normal).copy(color = p.ink2))
                    Text("${r.code.take(3)} ${r.code.drop(3)}", Modifier.padding(top = 12.dp), style = Ty.mono(34, FontWeight.Bold).copy(color = p.ink))
                }
            },
            confirmButton = { Btn("Link", go = true) { if (perms.all(c::has)) done(true) else ask() } },
            dismissButton = { TextBtn("Cancel") { hub.pending.value = null } },
        )
    }
}
