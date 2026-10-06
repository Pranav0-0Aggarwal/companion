package app.companion.ui.screens

import app.companion.data.lines
import app.companion.data.Line
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.ai.DrillBox
import app.companion.core.Category
import app.companion.core.Fingerprint
import app.companion.core.Span
import app.companion.data.Card
import app.companion.data.brand
import app.companion.data.Item
import app.companion.data.Profile
import app.companion.data.moved
import app.companion.data.credit
import app.companion.data.tagList
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.clock
import app.companion.ui.dateOf
import app.companion.ui.dayLabel
import app.companion.ui.inr
import app.companion.ui.kit.Chip
import app.companion.ui.kit.Ic
import app.companion.ui.kit.Ink
import app.companion.ui.kit.MoneyRow
import app.companion.ui.kit.Pace
import app.companion.ui.kit.AmountPair
import app.companion.ui.kit.Btn
import app.companion.ui.kit.LocalSnack
import app.companion.ui.kit.Screen
import app.companion.ui.kit.Stamp
import app.companion.ui.kit.ToolButton
import app.companion.ui.kit.empty
import app.companion.ui.kit.part
import app.companion.ui.Pick
import app.companion.ui.monthName
import app.companion.ui.pal
import app.companion.ui.shortDay
import app.companion.ui.today
import app.companion.ui.zone
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch
import app.companion.ui.money as amt

private fun Card.has(i: Item) = i.last4 == last4 && (i.bank == null || bank.contains(i.bank, true) || i.bank.contains(bank, true))

private fun YearMonth.ms() = atDay(1).atStartOfDay(zone()).toInstant().toEpochMilli()

private fun mode(m: String) = when (m) {
    "Upi" -> "UPI"
    "Netbanking" -> "net banking"
    else -> m.lowercase()
}

private fun Item.spend() = !credit && !moved && currency == "INR"

private fun sums(items: List<Item>, ym: YearMonth): LongArray {
    val a = LongArray(ym.lengthOfMonth())
    val from = ym.ms()
    val to = ym.plusMonths(1).ms()
    items.forEach { if (it.at in from until to && it.spend()) a[dateOf(it.at).dayOfMonth - 1] += it.paise }
    for (i in 1 until a.size) a[i] += a[i - 1]
    return a
}

internal class Pacing(val cur: List<Long>, val typ: List<Long>, val days: Int, val delta: Long, val day: Int)

private fun pacing(money: List<Item>, ym: YearMonth, now: LocalDate): Pacing? {
    val n = ym.lengthOfMonth()
    val past = (1..3).map { sums(money, ym.minusMonths(it.toLong())) }.filter { it.last() > 0 }
    if (past.isEmpty()) return null
    val typ = List(n) { i -> past.sumOf { a -> a[minOf(i, a.size - 1)] } / past.size }
    val upto = if (YearMonth.from(now) == ym) now.dayOfMonth else n
    val cur = sums(money, ym).take(upto)
    return Pacing(cur, typ, n, cur.last() - typ[upto - 1], upto)
}

@Composable
fun LedgerScreen(go: (String) -> Unit, lead: @Composable () -> Unit) {
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
    var parked by rememberSaveable { mutableStateOf(false) }
    val keys by repo.moved.collectAsStateWithLifecycle(emptySet())
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val ask = LocalAsk.current
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
    val pick by Pick.item.collectAsStateWithLifecycle()
    LaunchedEffect(pick) {
        pick?.let {
            back = 0
            open = it
            Pick.item.value = null
        }
    }
    val now = today()
    val ym = YearMonth.from(now).minusMonths(back.toLong())
    val inMonth = remember(money, ym) { money.filter { it.at >= ym.ms() && it.at < ym.plusMonths(1).ms() } }
    val card = cards.firstOrNull { it.id == acct }
    val base = remember(inMonth, card, cat, who) {
        inMonth.filter { (card == null || card.has(it)) && (cat == null || it.category == cat) && (who == null || it.title.contains(who!!, true)) }
    }
    val rows = remember(base, parked) { if (parked) base.filter { it.moved } else base }
    val days = remember(rows) { rows.groupBy { dateOf(it.at) }.toList() }
    val cats = remember(inMonth) { Category.entries.map { it.label }.filter { l -> inMonth.any { it.category == l } } }
    val links = rememberLinks(rows)
    val all = card == null && cat == null && who == null && !parked
    val moved = base.away()
    val spent = if (parked) moved else rows.tot(false)
    val budget = prof.budget.takeIf { all }
    val pace = remember(money, ym, all) { if (all) pacing(money, ym, now) else null }
    val span = Span(ym.atDay(1), if (back == 0) now else ym.atEndOfMonth())
    Screen(
        "Money",
        "${shortDay(span.from)} to ${shortDay(span.to)} · ${inr(spent)} ${if (parked) "moved" else "spent"}",
        lead = lead,
        tall = false,
        tools = { ToolButton(Ic.Search, "Search or ask") { ask(AskReq(null)) } },
    ) {
        item(key = "ask") { AskPill(hint = "Ask about your spending") }
        item(key = "month") {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${monthName(ym.atDay(1))} ${ym.year}", Modifier.weight(1f), style = Ty.ui(20, FontWeight.Bold).copy(color = p.ink))
                ToolButton(Ic.Left, "Previous month") { back++ }
                if (back > 0) ToolButton(Ic.Right, "Next month") { back-- }
            }
        }
        item(key = "sum") { Summary(spent, if (parked) 0 else rows.tot(true), budget, pace, moved, parked, { parked = !parked }, Modifier.part(p, true, true)) }
        item(key = "chips") {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip("All accounts", card == null) { acct = 0 }
                cards.forEach { c -> Chip("${c.bank} ··${c.last4}", acct == c.id) { acct = if (acct == c.id) 0 else c.id } }
                who?.let { w -> Chip(w, true, icon = Ic.Close) { who = null } }
                cats.forEach { c -> Chip(c.cap(), cat == c, icon = Ic.of(c)) { cat = if (cat == c) null else c } }
            }
        }
        if (rows.isEmpty()) {
            empty(
                Ic.Ledger,
                if (all) "Nothing in ${monthName(ym.atDay(1))}" else "Nothing matches",
                if (all) "Payments appear here as their messages arrive." else "Try another account or category.",
            )
        }
        days.forEach { (day, list) ->
            item(key = "d$day") {
                Text(
                    if (day == now) "Today · ${dayLabel(day)}" else dayLabel(day),
                    Modifier.padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 8.dp),
                    style = Ty.ui(14).copy(color = p.ink2),
                )
            }
            val lines = list.lines()
            lines.forEachIndexed { k, l ->
                val i = if (l is Line.Moved) l.out else (l as Line.One).i
                item(key = i.id, contentType = "txn") {
                    Column(Modifier.animateItem().part(p, k == 0, k == lines.lastIndex)) {
                        val tap = { open = if (open == i.id) -1 else i.id }
                        if (l is Line.Moved) MoneyRow(l.title, l.sub, amt(i.paise, i.currency), false, lead = Ic.Swap, moved = true, sign = false, onClick = tap)
                        else MoneyRow(
                            i.shown(), i.srcLine(links), amt(i.paise, i.currency), i.credit,
                            lead = Ic.of(i.category), brand = i.brand, stamp = i.stamp(), tags = i.tagged(), moved = i.moved,
                            onClick = tap,
                        )
                        AnimatedVisibility(open == i.id, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                            Detail(i, cards.firstOrNull { it.has(i) }, links, keys, { off -> scope.launch { repo.spending(i.id, off) } }, { open = -1; snack.go { repo.delete(i.id)?.let { snack.offer(it, "Deleted") { r -> repo.restore(r) } } } }) { c -> snack.teach(repo) { repo.file(i.id, c) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Summary(spent: Long, inn: Long, budget: Long?, pace: Pacing?, away: Long, parked: Boolean, onParked: () -> Unit, modifier: Modifier) {
    val p = pal
    Column(modifier.padding(20.dp)) {
        AmountPair(
            (if (parked) "Moved" else "Spent") to inr(spent),
            if (inn > 0) "In" to inr(inn) else null,
            Ty.mono(36, FontWeight.Bold).copy(color = p.ink),
            Ty.mono(18, FontWeight.SemiBold).copy(color = p.green),
            Ty.ui(13, FontWeight.Normal).copy(color = p.ink2),
        )
        if (away > 0) {
            Text(
                if (parked) "Showing moved items · Show all" else "${inr(away)} moved · card bills, own accounts, investments",
                Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onParked).padding(top = 10.dp, bottom = 4.dp),
                style = Ty.ui(13, FontWeight.Normal).copy(color = if (parked) p.accent else p.ink2),
            )
        }
        if (pace != null) {
            val word = if (pace.delta >= 0) "above" else "below"
            val line = "${inr(kotlin.math.abs(pace.delta))} $word a typical month by day ${pace.day}"
            Pace(pace.cur, pace.typ, pace.days, line, Modifier.padding(top = 14.dp))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp, 3.dp).background(p.accent, RoundedCornerShape(2.dp)))
                Text("This month", Modifier.padding(start = 6.dp, end = 14.dp), style = Ty.ui(12, FontWeight.Medium).copy(color = p.ink2))
                Box(Modifier.size(10.dp, 2.dp).background(p.ink2, RoundedCornerShape(1.dp)))
                Text("Typical month", Modifier.padding(start = 6.dp), style = Ty.ui(12, FontWeight.Medium).copy(color = p.ink2))
            }
            Text(line, Modifier.padding(top = 6.dp), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink))
        }
        if (budget != null) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${(spent * 100 / budget.coerceAtLeast(1))}% of ${inr(budget)} budget", Modifier.weight(1f), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2))
                if (spent > budget) Stamp("OVER", ink = Ink.Red)
            }
        }
    }
}

@Composable
private fun Detail(i: Item, card: Card?, links: Links, moved: Set<String>, onSpend: (Boolean) -> Unit, onDelete: () -> Unit, onFile: (String) -> Unit) {
    val p = pal
    var sheet by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    val key = i.merchant?.let { Fingerprint.norm(it) }
    val d = dateOf(i.at)
    val lines = listOf(
        "${dayLabel(d)} ${d.year}, ${clock(i.at)}",
        listOfNotNull(i.mode?.let(::mode), card?.let { "${it.nick.ifBlank { it.bank }} ··${it.last4}" } ?: i.acct()).joinToString(" · "),
        "via ${i.via(links)}",
    ).filter { it.isNotEmpty() }
    Column(Modifier.fillMaxWidth().background(p.raised).padding(start = 72.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
        lines.forEach { Text(it, style = Ty.mono(13, FontWeight.Normal).copy(color = p.ink2)) }
        Text("File under", Modifier.padding(top = 10.dp, bottom = 6.dp), style = Ty.ui(13).copy(color = p.ink))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Category.entries.filter { it != Category.Income || i.credit }.forEach { c ->
                Chip(c.label.cap(), i.category == c.label, icon = Ic.of(c.label)) { if (i.category != c.label) onFile(c.label) }
            }
        }
        FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Btn("This is…", dense = true) { sheet = true }
            if (i.merchant != null) Btn("Rename merchant", dense = true) { rename = true }
            if (key != null && i.kind == "Debit") Chip("Not spending", key in moved) { onSpend(key !in moved) }
            Btn("Delete", dense = true, onClick = onDelete)
        }
        if (key != null && key in moved) {
            Text("Everything to ${i.merchant}, past and future, stays out of Spent.", Modifier.padding(top = 6.dp), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2))
        }
        DupPanel(i, Modifier.padding(top = 12.dp))
    }
    if (sheet) TypeSheet(i) { sheet = false }
    if (rename) RenameSheet(i) { rename = false }
}
