package app.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.core.Category
import app.companion.core.Span
import app.companion.data.Card
import app.companion.data.Item
import app.companion.data.Profile
import app.companion.data.credit
import app.companion.ai.DrillBox
import app.companion.sl
import java.time.temporal.ChronoUnit
import app.companion.ui.Ty
import app.companion.ui.clock
import app.companion.ui.dateOf
import app.companion.ui.dayLabel
import app.companion.ui.inr
import app.companion.ui.kit.Chip
import app.companion.ui.kit.Ic
import app.companion.ui.kit.LedgerHead
import app.companion.ui.kit.LedgerRow
import app.companion.ui.kit.LedgerTotal
import app.companion.ui.kit.Screen
import app.companion.ui.kit.Stamp
import app.companion.ui.kit.ToolButton
import app.companion.ui.monthName
import app.companion.ui.pal
import app.companion.ui.plain
import app.companion.ui.shortDay
import app.companion.ui.today
import app.companion.ui.zone
import java.time.YearMonth
import kotlinx.coroutines.launch

private fun Card.has(i: Item) = i.last4 == last4 && (i.bank == null || bank.contains(i.bank, true) || i.bank.contains(bank, true))

private fun YearMonth.ms() = atDay(1).atStartOfDay(zone()).toInstant().toEpochMilli()

private fun mode(m: String) = when (m) {
    "Upi" -> "UPI"
    "Netbanking" -> "net banking"
    else -> m.lowercase()
}

@Composable
fun LedgerScreen(go: (String) -> Unit) {
    val p = pal
    val repo = LocalContext.current.sl.repo
    val prof by repo.profile.collectAsStateWithLifecycle(Profile())
    val cards by repo.cards.collectAsStateWithLifecycle(emptyList())
    val money by repo.money.collectAsStateWithLifecycle(emptyList())
    var back by rememberSaveable { mutableIntStateOf(0) }
    var acct by rememberSaveable { mutableLongStateOf(0L) }
    var cat by rememberSaveable { mutableStateOf<String?>(null) }
    var open by rememberSaveable { mutableLongStateOf(-1L) }
    var who by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val drill by DrillBox.pending.collectAsStateWithLifecycle()
    LaunchedEffect(drill, cards) {
        drill?.let { d ->
            cat = d.category
            who = d.merchant
            acct = cards.firstOrNull { it.last4 == d.last4 }?.id ?: 0L
            back = ChronoUnit.MONTHS.between(YearMonth.from(d.start), YearMonth.from(today())).toInt()
            DrillBox.pending.value = null
        }
    }
    val now = today()
    val ym = YearMonth.from(now).minusMonths(back.toLong())
    val inMonth = remember(money, ym) { money.filter { it.at >= ym.ms() && it.at < ym.plusMonths(1).ms() } }
    val card = cards.firstOrNull { it.id == acct }
    val rows = remember(inMonth, card, cat, who) {
        inMonth.filter { (card == null || card.has(it)) && (cat == null || it.category == cat) && (who == null || it.title.contains(who!!, true)) }
    }
    val cats = remember(inMonth) { Category.entries.map { it.label }.filter { l -> inMonth.any { it.category == l } } }
    val links = rememberLinks(rows)
    val all = card == null && cat == null && who == null
    val spent = rows.tot(false)
    val budget = prof.budget.takeIf { all }
    val span = Span(ym.atDay(1), if (back == 0) now else ym.atEndOfMonth())
    Screen(
        "Ledger",
        "${shortDay(span.from)} to ${shortDay(span.to)}",
        tools = { ToolButton(Ic.Search, "Search") { go("search") } },
    ) {
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip("All accounts", card == null) { acct = 0 }
                cards.forEach { c -> Chip("${c.bank} ··${c.last4}", acct == c.id) { acct = c.id } }
                who?.let { w -> Chip("$w ✕", true) { who = null } }
                cats.forEach { c -> Chip(c.cap(), cat == c) { cat = if (cat == c) null else c } }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 14.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(monthName(ym.atDay(1)), Modifier.weight(1f), style = Ty.ui(20, FontWeight.ExtraBold).copy(color = p.ink))
                    Chip("‹ ${monthName(ym.minusMonths(1).atDay(1)).take(3)}", false) { back++ }
                    if (back > 0) Chip("${monthName(ym.plusMonths(1).atDay(1)).take(3)} ›", false) { back-- }
                }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val head = listOfNotNull("spent ${inr(spent)}", "in ${inr(rows.tot(true))}", budget?.let { "of ${inr(it)}" })
                    Text(head.joinToString(" · "), Modifier.weight(1f), style = Ty.mono(12).copy(color = p.ink2))
                    if (budget != null && spent > budget) Stamp("OVER")
                }
            }
        }
        item { LedgerHead() }
        if (rows.isEmpty()) {
            item { Quiet(if (all) "Nothing printed in ${monthName(ym.atDay(1))}" else "Nothing matches these filters") }
        } else {
            items(rows, key = { it.id }) { i ->
                Column {
                    LedgerRow(
                        dateOf(i.at).dayOfMonth.toString().padStart(2, '0'), i.title, i.srcLine(links),
                        if (i.credit) "" else plain(i.paise), if (i.credit) plain(i.paise) else "",
                        onClick = { open = if (open == i.id) -1 else i.id },
                    )
                    if (open == i.id) Detail(i, cards.firstOrNull { it.has(i) }, links) { cat -> scope.launch { repo.file(i.id, cat) } }
                }
            }
            item { LedgerTotal("Total", plain(spent), plain(rows.tot(true))) }
        }
    }
}

@Composable
private fun Detail(i: Item, card: Card?, links: Links, onFile: (String) -> Unit) {
    val p = pal
    val d = dateOf(i.at)
    val lines = listOf(
        "${dayLabel(d)} ${d.year}, ${clock(i.at)}",
        listOfNotNull(i.mode?.let(::mode), card?.let { "${it.nick.ifBlank { it.bank }} ··${it.last4}" } ?: i.acct()).joinToString(" · "),
        listOfNotNull(i.category, i.via(links)).joinToString(" · "),
    ).filter { it.isNotEmpty() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(Modifier.fillMaxWidth().background(p.ruleSoft).padding(start = 67.dp, end = 12.dp, top = 6.dp, bottom = 8.dp)) {
            lines.forEach { Text(it, style = Ty.mono(11).copy(color = p.ink)) }
        }
        Row(
            Modifier.fillMaxWidth().background(p.ruleSoft).horizontalScroll(rememberScrollState()).padding(start = 67.dp, end = 12.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Category.entries.filter { it != Category.Income || i.credit }.forEach { c ->
                Chip(c.label.cap(), i.category == c.label) { if (i.category != c.label) onFile(c.label) }
            }
        }
    }
}
