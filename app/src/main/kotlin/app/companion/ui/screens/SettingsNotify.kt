package app.companion.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.companion.core.Opts
import app.companion.system.Live
import app.companion.system.Prefs
import app.companion.ui.has
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Group
import app.companion.ui.kit.Ic
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Rule
import app.companion.ui.kit.Section
import app.companion.ui.kit.Toggle
import app.companion.ui.rememberPerms
import kotlinx.coroutines.launch

@Composable
fun NotifySettings() {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var o by remember { mutableStateOf(Prefs.get(c)) }
    var granted by remember { mutableStateOf(c.has(Manifest.permission.POST_NOTIFICATIONS)) }
    LifecycleResumeEffect(Unit) {
        granted = c.has(Manifest.permission.POST_NOTIFICATIONS)
        onPauseOrDispose {}
    }
    val ask = rememberPerms(Manifest.permission.POST_NOTIFICATIONS) { granted = it }
    val set: (Opts) -> Unit = { n ->
        o = n
        Prefs.set(c, n)
        scope.launch { Live.widgets(c) }
    }
    Column {
        Section("Notifications")
        Group {
            if (!granted) {
                Btn("Turn on notifications", Modifier.padding(18.dp), go = true) { ask() }
                Rule()
            }
            Toggle("Spending", "A quiet note for each payment", o.spend, icon = Ic.Ledger) { set(o.copy(spend = it)) }
            Rule(72.dp)
            Toggle("New bills", "When a bill or card statement arrives", o.bill, icon = Ic.Bills) { set(o.copy(bill = it)) }
            Rule(72.dp)
            Toggle("Bill reminders", "Three days out, then tomorrow and today", o.remind, icon = Ic.Bell) { set(o.copy(remind = it)) }
            Rule(72.dp)
            Toggle("Deliveries", "Out for delivery with the code, and delivered", o.delivery, icon = Ic.Bolt) { set(o.copy(delivery = it)) }
            Rule(72.dp)
            Toggle("Daily summary", "At 8 pm, only when something is waiting", o.digest, icon = Ic.Plan) { set(o.copy(digest = it)) }
            Rule(72.dp)
            Toggle("Show details on lock screen", "Amounts, merchants and codes stay hidden when off", o.lock, icon = Ic.Lock) { set(o.copy(lock = it)) }
            Rule(72.dp)
            PassLine(
                "System notification settings",
                "Sounds and channels",
                lead = Ic.Settings,
                onClick = { c.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)) },
            )
        }
        Section("Cover screen")
        Group {
            Toggle("Hide amounts on cover screen", "Add the widget in Settings, Cover screen, Widgets", o.hide, icon = Ic.Lock) { set(o.copy(hide = it)) }
        }
    }
}
