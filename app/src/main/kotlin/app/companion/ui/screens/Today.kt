package app.companion.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import app.companion.data.bill
import app.companion.sl
import app.companion.ui.Secure
import app.companion.ui.Voice
import app.companion.ui.kit.Group
import app.companion.ui.kit.Ic
import app.companion.ui.kit.Rule
import app.companion.ui.kit.Screen
import app.companion.ui.kit.Section
import app.companion.ui.kit.ToolButton
import kotlinx.coroutines.launch

@Composable
fun TodayScreen(go: (String) -> Unit) {
    Secure()
    val repo = LocalContext.current.sl.repo
    val scope = rememberCoroutineScope()
    val d = rememberDay()
    Screen(
        "Today",
        d.sub,
        tools = {
            ToolButton(Ic.Search, "Search") { go("search") }
            ToolButton(Ic.More, "Settings") { go("settings") }
        },
    ) {
        item { Otps(d.otps) }
        item {
            Section("Needs you", if (d.need.isEmpty()) null else "All ${d.need.size}") {
                go(if (d.asks.isEmpty()) "bills" else "inbox")
            }
        }
        item {
            if (d.need.isEmpty()) {
                Quiet(Voice.addr(d.name, "nothing needs you right now"))
            } else {
                Group {
                    d.need.take(3).forEachIndexed { k, i ->
                        if (k > 0) Rule()
                        if (i.bill) {
                            BillLine(i, d.today) { scope.launch { repo.pay(i.id) } }
                        } else {
                            AskLine(i, d.links) { c -> scope.launch { repo.file(i.id, c) } }
                        }
                    }
                }
            }
        }
        item { Section("Printed today", "Ledger") { go("ledger") } }
        item { TodayLines(d.printed, d.links) }
    }
}
