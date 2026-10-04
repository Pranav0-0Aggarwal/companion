package app.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.core.Cycle
import app.companion.data.Card
import app.companion.data.Item
import app.companion.data.Profile
import app.companion.data.credit
import app.companion.data.dueDate
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.Voice
import app.companion.ui.dateOf
import app.companion.ui.inr
import app.companion.ui.kit.Group
import app.companion.ui.kit.Ic
import app.companion.ui.kit.LedgerHead
import app.companion.ui.kit.LedgerRow
import app.companion.ui.kit.Screen
import app.companion.ui.kit.Stamp
import app.companion.ui.kit.ToolButton
import app.companion.ui.money
import app.companion.ui.pal
import app.companion.ui.plain
import app.companion.ui.shortDay
import app.companion.ui.today
import java.time.LocalDate
import kotlinx.coroutines.launch

private class Cyc(val card: Card, val rows: List<Item>, val stmt: Item?, day: LocalDate) {
    val span = Cycle.span(card.stmtDay, day)
    val due = stmt?.dueDate ?: Cycle.due(card.stmtDay, card.dueDay, day)
    val spent = rows.filter { !it.credit && it.currency == "INR" }.sumOf { it.paise }
}

private fun cycle(c: Card, spends: List<Item>, bills: List<Item>, day: LocalDate): Cyc {
    val span = Cycle.span(c.stmtDay, day)
    val rows = spends.filter { it.last4 == c.last4 && (it.bank == null || it.bank == c.bank) && dateOf(it.at) in span.from..span.to }
    return Cyc(c, rows, bills.filter { it.kind == "Statement" && it.last4 == c.last4 }.maxByOrNull { it.at }, day)
}

@Composable
fun CardsScreen(go: (String) -> Unit) {
    val c = LocalContext.current
    val repo = c.sl.repo
    val scope = rememberCoroutineScope()
    val cards by repo.cards.collectAsStateWithLifecycle(emptyList<Card>())
    val spends by repo.money.collectAsStateWithLifecycle(emptyList<Item>())
    val bills by repo.bills.collectAsStateWithLifecycle(emptyList<Item>())
    val profile by repo.profile.collectAsStateWithLifecycle(Profile())
    val day = remember { today() }
    val cycles = remember(cards, spends, bills) { cards.map { cycle(it, spends, bills, day) } }
    val pager = rememberPagerState { cycles.size }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Card?>(null) }
    val sub = if (cards.isEmpty()) "No cards yet" else "${inr(cycles.sumOf { it.spent })} spent this cycle · ${cards.size} card${if (cards.size == 1) "" else "s"}"
    Screen("Cards", sub, tools = { ToolButton(Ic.Add, "Add a card") { adding = true } }) {
        val sel = cycles.getOrNull(pager.currentPage)
        if (sel == null) {
            emptyPage("No cards yet", Voice.addr(profile.name, "add a credit card to see its cycle, limit and due date"))
        } else {
            item(key = "faces") { Faces(cycles, pager) { deleting = it } }
            item(key = "cycle") { CycleBlock(sel) }
            item(key = "head") { LedgerHead() }
            if (sel.rows.isEmpty()) {
                item(key = "none") { Text("No transactions on this card this cycle", Modifier.padding(20.dp), style = Ty.mono(12).copy(color = pal.ink2)) }
            }
            items(sel.rows, key = { "t${it.id}" }) { t ->
                val amount = if (t.currency == "INR") plain(t.paise) else money(t.paise, t.currency)
                LedgerRow(
                    shortDay(dateOf(t.at)), t.title, listOfNotNull(t.category, srcWord(t.src)).joinToString(" · "),
                    if (t.credit) "" else amount, if (t.credit) amount else "",
                )
            }
        }
    }
    if (adding) AddCard({ adding = false }) { card -> scope.launch { repo.addCard(card) }; adding = false }
    deleting?.let { d ->
        PassConfirm(
            "Delete ${d.bank} ••${d.last4}?",
            "The card goes from Companion. Its transactions stay in your ledger.",
            "Delete",
            { scope.launch { repo.deleteCard(d.id) }; deleting = null },
            { deleting = null },
        )
    }
}

@Composable
private fun Faces(cycles: List<Cyc>, pager: PagerState, onDelete: (Card) -> Unit) {
    HorizontalPager(
        pager,
        Modifier.padding(top = 14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        pageSize = PageSize.Fixed(300.dp),
        pageSpacing = 12.dp,
        verticalAlignment = Alignment.Top,
    ) { i -> cycles.getOrNull(i)?.let { Face(it) { onDelete(it.card) } } }
}

private fun tint(bank: String) = when {
    bank.startsWith("HDFC") -> listOf(Color(0xFF0E2A5A), Color(0xFF1D4C9A))
    bank.startsWith("ICICI") -> listOf(Color(0xFF5A1A12), Color(0xFF9B3A1E))
    else -> listOf(Color(0xFF1F3A68), Color(0xFF3A5F9E))
}

@Composable
private fun Face(y: Cyc, onDelete: () -> Unit) {
    val p = pal
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.width(300.dp).heightIn(min = 180.dp).shadow(8.dp, shape).background(Brush.linearGradient(tint(y.card.bank)), shape)
            .combinedClickable(onLongClickLabel = "Delete card", onLongClick = onDelete) {}.padding(18.dp),
    ) {
        Column(Modifier.padding(bottom = 52.dp)) {
            Text(y.card.bank, style = Ty.ui(15, FontWeight.ExtraBold).copy(color = p.coverInk, letterSpacing = 0.02.sp))
            if (y.card.nick.isNotBlank()) Text(y.card.nick, Modifier.padding(top = 2.dp), style = Ty.ui(12, FontWeight.Medium).copy(color = p.coverMute))
            Box(Modifier.padding(top = 18.dp).size(38.dp, 28.dp).background(p.foil, RoundedCornerShape(6.dp)))
        }
        Text("•••• ${y.card.last4}", Modifier.align(Alignment.BottomStart), style = Ty.mono(18, FontWeight.Bold).copy(color = p.coverInk, letterSpacing = 0.16.em))
        Column(Modifier.align(Alignment.BottomEnd), horizontalAlignment = Alignment.End) {
            Text("due", style = Ty.ui(11).copy(color = p.coverMute))
            Text(shortDay(y.due), style = Ty.mono(16, FontWeight.ExtraBold).copy(color = p.coverInk))
        }
    }
}

@Composable
private fun CycleBlock(y: Cyc) {
    val p = pal
    val limit = y.card.limit?.takeIf { it > 0 }
    Group(Modifier.padding(top = 12.dp, bottom = 6.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(inr(y.spent), Modifier.weight(1f), style = Ty.mono(22, FontWeight.ExtraBold).copy(color = p.ink))
                Stamp("DUE ${shortDay(y.due).uppercase()}")
            }
            Text("statement ${shortDay(y.span.from)} to ${shortDay(y.span.to)}", Modifier.padding(top = 2.dp), style = Ty.ui(12, FontWeight.Medium).copy(color = p.ink2))
            if (limit != null) {
                Box(Modifier.padding(top = 10.dp, bottom = 6.dp).fillMaxWidth().height(8.dp).background(p.ruleSoft, RoundedCornerShape(4.dp))) {
                    Box(Modifier.fillMaxWidth((y.spent.toFloat() / limit).coerceIn(0f, 1f)).height(8.dp).background(p.accent, RoundedCornerShape(4.dp)))
                }
                Text("${y.spent * 100 / limit}% of ${inr(limit)} limit used", style = Ty.ui(12, FontWeight.Medium).copy(color = p.ink2))
            }
            y.stmt?.let { s ->
                val min = s.minPaise?.let { " · minimum ${inr(it)}" }.orEmpty()
                Text("Latest statement ${inr(s.paise)}$min", Modifier.padding(top = 8.dp), style = Ty.ui(12, FontWeight.Medium).copy(color = p.ink2))
            }
        }
    }
}
