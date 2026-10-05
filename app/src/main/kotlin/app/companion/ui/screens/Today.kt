package app.companion.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.companion.sl
import app.companion.ui.clock
import app.companion.ui.kit.Ic
import app.companion.ui.kit.LocalSnack
import app.companion.ui.kit.MoneyRow
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Screen
import app.companion.ui.kit.Section
import app.companion.ui.kit.ToolButton
import app.companion.ui.kit.Tone
import app.companion.ui.kit.part
import app.companion.ui.pal
import app.companion.ui.rememberNow
import app.companion.data.credit
import app.companion.data.tagList
import kotlinx.coroutines.launch
import app.companion.ui.money as amt

@Composable
fun TodayScreen(go: (String) -> Unit) {
    val p = pal
    val repo = LocalContext.current.sl.repo
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    val ask = LocalAsk.current
    val d = rememberDay()
    val now = rememberNow(1000, active = d.otps.isNotEmpty())
    val live by remember(d.otps) { derivedStateOf(structuralEqualityPolicy()) { d.otps.filter { (it.expires ?: 0) > now.value } } }
    val need = d.need.take(5)
    Screen(
        d.hello,
        d.sub,
        bar = "Today",
        tools = {
            ToolButton(Ic.Search, "Search or ask") { ask(AskReq(null)) }
            ToolButton(Ic.Settings, "Settings") { go("settings") }
        },
    ) {
        item(key = "proc", contentType = "proc") { ProcessSlot() }
        item(key = "ask", contentType = "ask") { AskPill(Modifier.padding(bottom = 4.dp)) }
        itemsIndexed(live, key = { _, o -> "o${o.id}" }, contentType = { _, _ -> "code" }) { _, o ->
            app.companion.ui.kit.CodeCard(o, { snack.teach(repo) { repo.notOtp(o.id) } }, Modifier.animateItem().padding(start = 16.dp, end = 16.dp, top = 12.dp))
        }
        item(key = "meet") { MeetCard() }
        item(key = "needh") { Section("Needs you", if (d.need.size > need.size) "See all ${d.need.size}" else null) { go(if (d.asks.isEmpty()) "bills" else "inbox") } }
        if (need.isEmpty()) {
            item(key = "clear") { PassLine("All clear", "Nothing needs you right now", Modifier.part(p, true, true).animateItem(), lead = Ic.Check, tone = Tone.Green) }
        }
        itemsIndexed(need, key = { _, i -> "n${i.id}" }, contentType = { _, _ -> "need" }) { k, i ->
            Box(Modifier.animateItem().part(p, k == 0, k == need.lastIndex)) {
                NeedRow(i, d, { scope.launch { repo.pay(i.id) } }) { c -> snack.teach(repo) { repo.file(i.id, c) } }
            }
        }
        item(key = "spendh") { Section("Spent today", "Ledger") { go("ledger") } }
        item(key = "spend") { Box(Modifier.part(p, true, d.printed.isEmpty())) { SpendHead(d.printed) } }
        itemsIndexed(d.printed, key = { _, i -> "t${i.id}" }, contentType = { _, _ -> "txn" }) { k, i ->
            MoneyRow(
                i.title, i.srcLine(d.links), amt(i.paise, i.currency), i.credit,
                Modifier.animateItem().part(p, false, k == d.printed.lastIndex),
                time = clock(i.at), stamp = i.stamp(), tags = i.tagList,
            )
        }
        item(key = "sugg") { Suggested() }
        item(key = "next") { NextUp(go) }
    }
}
