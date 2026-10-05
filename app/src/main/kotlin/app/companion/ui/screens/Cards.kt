package app.companion.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.core.Cycle
import app.companion.core.Found
import app.companion.data.Card
import app.companion.data.brand
import app.companion.data.Item
import app.companion.data.Profile
import app.companion.data.credit
import app.companion.data.dueDate
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.Voice
import app.companion.ui.dateOf
import app.companion.ui.inr
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Group
import app.companion.ui.kit.Ic
import app.companion.ui.kit.Ink
import app.companion.ui.kit.LocalSnack
import app.companion.ui.kit.MoneyRow
import app.companion.ui.kit.Motion
import app.companion.ui.kit.Amount
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Rule
import app.companion.ui.kit.Screen
import app.companion.ui.kit.Section
import app.companion.ui.kit.Stamp
import app.companion.ui.kit.TextBtn
import app.companion.ui.kit.ToolButton
import app.companion.ui.kit.Tone
import app.companion.ui.kit.empty
import app.companion.ui.kit.part
import app.companion.ui.money
import app.companion.ui.pal
import app.companion.ui.shortDay
import app.companion.ui.today
import java.time.LocalDate
import kotlin.math.absoluteValue
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

private fun face(bank: String) = when {
    bank.startsWith("HDFC") -> Color(0xFF14305E)
    bank.startsWith("ICICI") -> Color(0xFF6A2A1C)
    bank.startsWith("SBI") -> Color(0xFF1B4F8A)
    bank.startsWith("Axis") -> Color(0xFF6B1E3D)
    bank.startsWith("Kotak") -> Color(0xFF7A1F27)
    bank.startsWith("American") || bank.startsWith("Amex") -> Color(0xFF1E5B6E)
    else -> Color(0xFF2B2F3A)
}

@Composable
fun CardsScreen(go: (String) -> Unit) {
    val p = pal
    val c = LocalContext.current
    val repo = c.sl.repo
    val scope = rememberCoroutineScope()
    val cards by repo.cards.collectAsStateWithLifecycle(emptyList<Card>())
    val spends by repo.money.collectAsStateWithLifecycle(emptyList<Item>())
    val bills by repo.bills.collectAsStateWithLifecycle(emptyList<Item>())
    val profile by repo.profile.collectAsStateWithLifecycle(Profile())
    val found by repo.found.collectAsStateWithLifecycle(emptyList<Found>())
    val snack = LocalSnack.current
    val day = remember { today() }
    val cycles = remember(cards, spends, bills) { cards.map { cycle(it, spends, bills, day) } }
    val pager = rememberPagerState { cycles.size }
    var adding by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf<Found?>(null) }
    var deleting by remember { mutableStateOf<Card?>(null) }
    val sub = if (cards.isEmpty()) found.size.takeIf { it > 0 }?.let { "Found $it in your messages" } ?: "No cards yet" else "${inr(cycles.sumOf { it.spent })} this cycle · ${cards.size} card${if (cards.size == 1) "" else "s"}"
    Screen("Cards", sub, tools = { ToolButton(Ic.Add, "Add a card") { adding = true } }) {
        val sel = cycles.getOrNull(pager.currentPage)
        val suggest = {
            suggestions(
                found, { f -> if (f.ready) scope.launch { repo.addFound(f) } else setup = f },
                { scope.launch { repo.atomic { found.filter { it.ready }.forEach { repo.addFound(it) } } } },
                { f -> scope.launch { repo.dismissFound(f) }; snack.offer(f, "Won't suggest ${f.bank} ··${f.last4} again") { repo.restoreFound(it) } },
            )
        }
        if (sel == null) {
            if (found.isNotEmpty()) suggest()
            val (t, b) = if (found.isEmpty()) "No cards yet" to "add a credit card to see its cycle, limit and due date." else "Not on the list?" to "add another card by hand to see its cycle, limit and due date."
            empty(Ic.Cards, t, Voice.addr(profile.name, b)) {
                Btn("Add a card", go = true, icon = Ic.Add) { adding = true }
            }
        } else {
            item(key = "faces") { Faces(cycles, pager) }
            item(key = "cycle") { CycleBlock(sel, Modifier.part(p, true, true)) { deleting = sel.card } }
            item(key = "head") {
                Text("This cycle", Modifier.padding(start = 28.dp, top = 22.dp, bottom = 8.dp), style = Ty.ui(14).copy(color = p.ink2))
            }
            if (sel.rows.isEmpty()) {
                item(key = "none") { Text("No spends on this card yet this cycle", Modifier.padding(horizontal = 28.dp, vertical = 8.dp), style = Ty.ui(14, FontWeight.Normal).copy(color = p.ink2)) }
            }
            itemsIndexed(sel.rows, key = { _, t -> "t${t.id}" }) { k, t ->
                MoneyRow(
                    t.title, listOfNotNull(shortDay(dateOf(t.at)), t.category, srcWord(t.src)).joinToString(" · "), money(t.paise, t.currency), t.credit,
                    Modifier.animateItem().part(p, k == 0, k == sel.rows.lastIndex), lead = Ic.of(t.category), brand = t.brand,
                )
            }
            if (found.isNotEmpty()) suggest()
        }
    }
    setup?.let { f -> AddCard({ setup = null }, f) { card -> scope.launch { repo.addCard(card) }; setup = null } }
    if (adding) AddCard({ adding = false }) { card -> scope.launch { repo.addCard(card) }; adding = false }
    deleting?.let { d ->
        PassConfirm(
            "Delete ${d.bank} ··${d.last4}?",
            "The card goes from Companion. Its transactions stay in your ledger.",
            "Delete",
            { scope.launch { repo.deleteCard(d.id) }; deleting = null },
            { deleting = null },
        )
    }
}

private fun LazyListScope.suggestions(found: List<Found>, add: (Found) -> Unit, addAll: () -> Unit, skip: (Found) -> Unit) {
    item(key = "foundh") { Section("Found ${found.size} ${if (found.size == 1) "card" else "cards"} in your messages", if (found.count { it.ready } > 1) "Add all" else null, addAll) }
    item(key = "found") {
        Group {
            found.forEachIndexed { k, f ->
                if (k > 0) Rule(72.dp)
                Suggest(f, { add(f) }) { skip(f) }
            }
        }
    }
}

@Composable
private fun Suggest(f: Found, add: () -> Unit, skip: () -> Unit) {
    val cycle = if (f.ready) "Statement on the ${ord(f.stmtDay!!)} · due the ${ord(f.dueDay!!)}" else "Cycle not in your messages yet"
    val limit = f.limit?.takeIf { it > 0 }?.let { "${inr(it)} limit" } ?: f.avail?.takeIf { it > 0 }?.let { "${inr(it)} available" }
    PassLine(
        "${f.bank} ··${f.last4}",
        listOfNotNull(cycle, limit).joinToString(" · "),
        brand = f.bank,
        tone = Tone.Plain,
        trailing = { Btn(if (f.ready) "Add" else "Set up", dense = true, go = true, onClick = add) },
        actions = { TextBtn("Not mine", color = pal.ink2, onClick = skip) },
    )
}

@Composable
private fun Faces(cycles: List<Cyc>, pager: PagerState) {
    Column {
        HorizontalPager(
            pager,
            Modifier.padding(top = 4.dp),
            contentPadding = PaddingValues(horizontal = 30.dp),
            pageSize = PageSize.Fill,
            pageSpacing = 12.dp,
            verticalAlignment = Alignment.Top,
        ) { i ->
            cycles.getOrNull(i)?.let { y ->
                Face(y, Modifier.graphicsLayer {
                    val off = ((pager.currentPage - i) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                    val s = 1f - 0.06f * off
                    scaleX = s
                    scaleY = s
                    alpha = 1f - 0.35f * off
                })
            }
        }
        if (cycles.size > 1) Dots(cycles.size, pager)
    }
}

@Composable
private fun Dots(n: Int, pager: PagerState) {
    val p = pal
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.Center) {
        repeat(n) { i ->
            val on = i == pager.currentPage
            val w by animateFloatAsState(if (on) 18f else 6f, Motion.snappy(), label = "dot")
            Box(Modifier.padding(horizontal = 3.dp).height(6.dp).width(w.dp).background(if (on) p.accent else p.line, RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun Face(y: Cyc, modifier: Modifier) {
    val base = face(y.card.bank)
    val ink = Color(0xFFF2F4F8)
    val mute = Color(0xFFC3CAD8)
    Box(
        modifier.fillMaxWidth().heightIn(min = 188.dp).clip(RoundedCornerShape(22.dp)).background(base)
            .semantics(mergeDescendants = true) { contentDescription = "${y.card.bank} card ending ${y.card.last4}, due ${shortDay(y.due)}" }
            .padding(20.dp),
    ) {
        Column(Modifier.padding(bottom = 52.dp)) {
            Text(y.card.bank, style = Ty.ui(16, FontWeight.Bold).copy(color = ink))
            if (y.card.nick.isNotBlank()) Text(y.card.nick, Modifier.padding(top = 2.dp), style = Ty.ui(13, FontWeight.Medium).copy(color = mute))
        }
        Text("•••• ${y.card.last4}", Modifier.align(Alignment.BottomStart), style = Ty.mono(19, FontWeight.SemiBold).copy(color = ink, letterSpacing = 3.sp))
        Column(Modifier.align(Alignment.BottomEnd), horizontalAlignment = Alignment.End) {
            Text("Due", style = Ty.ui(12, FontWeight.Medium).copy(color = mute))
            Text(shortDay(y.due), style = Ty.mono(17, FontWeight.Bold).copy(color = ink))
        }
    }
}

@Composable
private fun CycleBlock(y: Cyc, modifier: Modifier, onDelete: () -> Unit) {
    val p = pal
    val limit = y.card.limit?.takeIf { it > 0 }
    Column(modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Amount(inr(y.spent), Ty.mono(32, FontWeight.Bold).copy(color = p.ink), Modifier.weight(1f))
            Stamp("DUE ${shortDay(y.due).uppercase()}", ink = Ink.Red)
        }
        Text("Statement ${shortDay(y.span.from)} to ${shortDay(y.span.to)}", Modifier.padding(top = 4.dp), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2))
        if (limit != null) {
            val f by animateFloatAsState((y.spent.toFloat() / limit).coerceIn(0f, 1f), Motion.soft(), label = "limit")
            Box(
                Modifier.padding(top = 14.dp).fillMaxWidth().height(8.dp).drawBehind {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(p.line, cornerRadius = r)
                    drawRoundRect(if (f > 0.9f) p.red else p.accent, size = Size(size.width * f, size.height), cornerRadius = r)
                },
            )
            Text("${y.spent * 100 / limit}% of ${inr(limit)} limit used", Modifier.padding(top = 8.dp), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2))
        }
        y.stmt?.let { s ->
            val min = s.minPaise?.let { " · minimum ${inr(it)}" }.orEmpty()
            Text("Latest statement ${inr(s.paise)}$min", Modifier.padding(top = 8.dp), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2))
        }
        TextBtn("Delete card", Modifier.padding(top = 4.dp), color = p.red, onClick = onDelete)
    }
}
