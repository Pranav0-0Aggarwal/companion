package app.companion.ui.screens

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.core.PowersStatus
import app.companion.link.LinkState
import app.companion.link.LinkStore
import app.companion.link.Stage
import app.companion.link.feature.Calls
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val perms = arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)

private class Row(val key: String, val label: String, val sub: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val perms: List<String> = emptyList(), val why: String = "")

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

private val more = listOf(
    Row("calls", "Calls", "Answer, place and talk through calls on your Mac", Ic.Mic, listOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.ANSWER_PHONE_CALLS, Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS), "Allow Phone and SMS access to turn on calls"),
    Row("live", "Live activities", "Deliveries, rides, timers and media on your Mac", Ic.Clock),
    Row("files", "Files", "Send files between this phone and your Mac", Ic.Download),
    Row("photos", "Screenshots", "New screenshots appear on your Mac", Ic.Image, listOf(Manifest.permission.READ_MEDIA_IMAGES), "Allow access to photos to turn on screenshots"),
    Row("clipImage", "Copied images", "Images you copy move between the two devices", Ic.Copy),
    Row("handoff", "Handoff", "Open pages from your Mac here, and send pages to it", Ic.Send, listOf(Manifest.permission.POST_NOTIFICATIONS), "Allow notifications to turn on Handoff"),
    Row("dnd", "Do Not Disturb", "Do Not Disturb follows between the two devices", Ic.Bell, why = "Allow Do Not Disturb access to turn this on"),
    Row("mirror", "Phone mirroring", "See and control this screen in a Mac window, needs Phone powers", Ic.Eye),
)

private val all = rows + more

private fun Row.ok(c: Context) = if (key == "dnd") c.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted else perms.all(c::has)

private fun Stage.sub(name: String?, hint: Boolean = false) = when (this) {
    Stage.Off -> "Off"
    Stage.Unpaired -> "Not paired"
    Stage.NoPermission -> "Allow Bluetooth to connect"
    Stage.BluetoothOff -> "Bluetooth is off"
    Stage.Waiting -> if (hint) "Waiting for $name. Open Notchy on your Mac" else "Waiting for $name"
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
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(s.stage) {
        slow = false
        if (s.stage == Stage.Waiting) {
            delay(30_000)
            slow = true
        }
    }
    val snack = LocalSnack.current
    var unpair by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf(false) }
    var listener by remember { mutableStateOf(c.listenerOn()) }
    var held by remember { mutableStateOf(all.filter { it.ok(c) }.map { it.key }.toSet()) }
    var granted by remember { mutableStateOf(hub.powers.granted()) }
    var want by rememberSaveable { mutableStateOf<String?>(null) }
    val up by hub.powers.bridge.up.collectAsStateWithLifecycle()
    val why by hub.powers.why.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val result = { r: Row, ok: Boolean ->
        if (ok) {
            held += r.key
            hub.share(r.key, true)
        } else if (activity != null && r.perms.any { !c.has(it) && !ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }) {
            snack.act(r.why, "Open settings") { c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", c.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        } else {
            snack.say(r.why)
        }
    }
    LifecycleResumeEffect(Unit) {
        listener = c.listenerOn()
        granted = hub.powers.granted()
        val on = LinkStore.shares(c)
        val now = all.filter { it.ok(c) }.map { it.key }.toSet()
        all.filter { it.key in on && it.key !in now }.forEach {
            hub.share(it.key, false)
            snack.say(it.why)
        }
        val calling = Calls.live?.busy() == true
        (now - held).filter { it in on && !(it == "calls" && calling) }.forEach(hub::restart)
        held = if (calling) now - "calls" else now
        all.firstOrNull { it.key == want && it.key == "dnd" }?.let { want = null; result(it, it.ok(c)) }
        hub.refresh()
        onPauseOrDispose {}
    }
    LaunchedEffect(s, up, why) { granted = hub.powers.granted() }
    val grant = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        all.firstOrNull { it.key == want && it.key != "dnd" }?.let { want = null; result(it, r.values.all { v -> v }) }
    }
    val pick = { r: Row, on: Boolean ->
        if (!on || r.key in held) hub.share(r.key, on)
        else {
            want = r.key
            if (r.key == "dnd") c.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            else grant.launch(r.perms.toTypedArray())
        }
    }
    val ask = rememberPerms(*perms) { ok -> if (ok) hub.enable(true) else snack.say("Companion needs Bluetooth permission for the link") }
    Screen("Mac link", "Nothing leaves your phone except to your paired Mac, end-to-end encrypted", back = back, nav = false, tall = false) {
        if (s.stage == Stage.Unpaired) {
            item(key = "empty") { Empty(Ic.Link, "Link your Mac", "In Notchy on your Mac, choose Pair phone. Scan the code with your phone camera and Companion opens to confirm.") }
        } else {
            item(key = "status") {
                Group {
                    PassLine(s.name ?: "Mac", s.stage.sub(s.name, slow), lead = Ic.Link, tone = if (s.stage == Stage.Connected) Tone.Green else Tone.Accent)
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
                    ShareRows(rows, s, pick)
                    if ("notifs" in s.shares) {
                        Rule(72.dp)
                        PassLine("Apps", if (s.apps.isEmpty()) "None chosen" else "${s.apps.size} chosen", lead = Ic.Inbox, onClick = { apps = true })
                    }
                }
            }
            item(key = "more") { Section("Continuity") }
            item(key = "more-list") { Group { ShareRows(more, s, pick) } }
            item(key = "powers") { Section("Phone powers") }
            item(key = "powers-line") {
                val (head, fix) = PowersStatus.of(up, s.stage == Stage.Connected, granted, why)
                Group { PassLine(head, fix ?: "Lets your Mac take calls and mirror this screen", lead = Ic.Shield, tone = if (up) Tone.Green else Tone.Accent) }
            }
            item(key = "privacy") { Section("Privacy") }
            item(key = "privacy-text") {
                Group {
                    Column(Modifier.padding(18.dp)) {
                        listOf(
                            "Bluetooth carries the link. Files, screenshots, call audio and mirroring go over your Wi-Fi straight to your Mac, only while you use them. Companion adds no internet access for the link.",
                            "Everything is encrypted between this phone and your Mac. Keys never leave the two devices.",
                            "Everything above is off until you turn it on. Ringing this phone from your Mac is always allowed.",
                            "Phone powers turns wireless debugging on for a few seconds to start Bridge, then off. Bridge answers only Companion and never stores audio or video.",
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
private fun ShareRows(list: List<Row>, s: LinkState, pick: (Row, Boolean) -> Unit) {
    list.forEachIndexed { n, r ->
        if (n > 0) Rule(72.dp)
        Toggle(r.label, r.sub, r.key in s.shares, icon = r.icon) { pick(r, it) }
    }
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
