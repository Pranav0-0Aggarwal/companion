package app.companion.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.ai.Answer
import app.companion.ai.Answers
import app.companion.data.Card
import app.companion.data.Item
import app.companion.data.credit
import app.companion.data.money
import app.companion.sl
import app.companion.ui.Recent
import app.companion.ui.Secure
import app.companion.ui.Ty
import app.companion.ui.dateOf
import app.companion.ui.kit.CardShape
import app.companion.ui.kit.Chip
import app.companion.ui.kit.Ic
import app.companion.ui.kit.MoneyRow
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Section
import app.companion.ui.kit.Skel
import app.companion.ui.kit.ToolButton
import app.companion.ui.kit.Tone
import app.companion.ui.kit.motion
import app.companion.ui.kit.part
import app.companion.ui.kit.rise
import app.companion.ui.pal
import app.companion.ui.rememberVoice
import app.companion.ui.shortDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.companion.ui.money as amt

private sealed interface Res {
    data object Busy : Res
    class Done(val answers: List<Answer>, val lost: Boolean) : Res
}

internal fun tries(cards: List<Card>) = listOf(
    "Food spend this month",
    "Bills due this week",
    "Top merchants last month",
    cards.firstOrNull()?.let { "${it.bank} card this cycle" } ?: "Card spends this month",
    "Remind me to pay rent on the 5th",
)

private fun Item.sub() = listOfNotNull(note.ifBlank { body.orEmpty() }.take(80).takeIf { it.isNotBlank() && !money }).plus(meta()).plus(shortDay(dateOf(at))).joinToString(" · ")

@Composable
fun AskSheet(r: AskReq, open: Boolean, close: () -> Unit, go: (String) -> Unit, drag: Animatable<Float, *>) {
    val p = pal
    val ctx = LocalContext.current
    val sl = ctx.sl
    val repo = sl.repo
    val scope = rememberCoroutineScope()
    val kb = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val d = LocalDensity.current
    var q by remember(r) { mutableStateOf(r.text) }
    var asked by remember(r) { mutableStateOf(r.text.takeIf { it.isNotBlank() }) }
    var listening by remember { mutableStateOf(false) }
    val recents = remember { mutableStateListOf<String>().apply { addAll(Recent.list(ctx)) } }
    val cards by repo.cards.collectAsStateWithLifecycle(emptyList())
    val submit = { s: String ->
        val t = s.trim()
        if (t.isNotEmpty()) {
            q = t
            asked = t
            Recent.add(ctx, t)
            recents.remove(t)
            recents.add(0, t)
            kb?.hide()
        }
    }
    val voice = rememberVoice { t ->
        listening = false
        t?.let(submit)
    }
    val listen = voice?.let { v -> { listening = true; v() } }
    LaunchedEffect(r) {
        if (r.voice && listen != null) {
            listen()
        } else if (r.text.isBlank()) {
            delay(220)
            runCatching { focus.requestFocus() }
        }
    }
    var live by remember { mutableStateOf("") }
    LaunchedEffect(q) {
        delay(250)
        live = q.trim()
    }
    val hits by remember(live) { if (live.length < 2) flowOf(emptyList()) else repo.search(live) }.collectAsStateWithLifecycle(emptyList())
    val res by produceState<Res?>(null, asked) {
        val a = asked
        if (a == null) {
            value = null
            return@produceState
        }
        value = Res.Busy
        value = withContext(Dispatchers.Default) {
            val plan = sl.planner.plan(a, System.currentTimeMillis())
            Res.Done(plan.takeIf { it.explicit }?.queries.orEmpty().map { Answers.run(it, repo) }, plan.lost)
        }
    }
    val leave = { route: String ->
        close()
        go(route)
    }
    Column(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        Box(
            Modifier.fillMaxWidth().height(28.dp).pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { scope.launch { if (drag.value > with(d) { 120.dp.toPx() }) close() else drag.animateTo(0f) } },
                ) { ch, dy ->
                    ch.consume()
                    scope.launch { drag.snapTo((drag.value + dy).coerceAtLeast(0f)) }
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(36.dp, 4.dp).background(p.ink3.copy(alpha = 0.6f), RoundedCornerShape(2.dp)))
        }
        Row(Modifier.padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Ask Companion", Modifier.weight(1f), style = Ty.ui(22, FontWeight.Bold).copy(color = p.ink))
            ToolButton(Ic.Close, "Close", close)
        }
        Input(q, { q = it }, { submit(q) }, listen, listening, Modifier.focusRequester(focus))
        LazyColumn(Modifier.weight(1f).imePadding(), contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)) {
            when {
                q.isBlank() -> {
                    item(key = "try") { Section("Try") }
                    item(key = "chips") {
                        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            tries(cards).forEachIndexed { i, s -> Chip(s, false, Modifier.rise(i)) { submit(s) } }
                        }
                    }
                    if (recents.isNotEmpty()) {
                        item(key = "rh") { Section("Recent", "Clear") { Recent.clear(ctx); recents.clear() } }
                        itemsIndexed(recents, key = { _, s -> "r$s" }) { i, s ->
                            PassLine(s, "", Modifier.part(p, i == 0, i == recents.lastIndex).animateItem(), lead = Ic.Clock, tone = Tone.Plain, onClick = { submit(s) })
                        }
                    }
                }
                asked == q.trim() -> {
                    when (val x = res) {
                        null, Res.Busy -> item(key = "busy") { Thinking() }
                        is Res.Done -> {
                            itemsIndexed(x.answers, key = { i, _ -> "a$asked$i" }) { i, a ->
                                AnswerCard(a, leave, Modifier.rise(i))
                            }
                            if (x.answers.isEmpty()) {
                                item(key = "none") {
                                    val msg = if (x.lost) "I couldn't work that out. Try \"food last month\" or \"bills due this week\"." else "No direct answer. Matching messages are below."
                                    Text(msg, Modifier.padding(horizontal = 28.dp, vertical = 12.dp).rise(), style = Ty.ui(14, FontWeight.Normal).copy(color = p.ink2))
                                }
                            }
                        }
                    }
                    hitsSection(hits, p)
                }
                else -> {
                    item(key = "go") {
                        PassLine("Ask \"${q.trim()}\"", "Answer from your ledger, bills and plans", Modifier.part(p, true, true), lead = Ic.Sparkle, onClick = { submit(q) })
                    }
                    hitsSection(hits, p)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.hitsSection(hits: List<Item>, p: app.companion.ui.Pal) {
    if (hits.isEmpty()) return
    item(key = "hh") { Section("In your messages · ${hits.size}") }
    itemsIndexed(hits, key = { _, i -> "h${i.id}" }) { k, i ->
        val first = k == 0
        val last = k == hits.lastIndex
        Box(Modifier.part(p, first, last)) {
            if (i.kind == "Otp" && (i.expires ?: 0L) > System.currentTimeMillis()) Secure()
            if (i.paise > 0) {
                MoneyRow(i.title, i.sub(), amt(i.paise, i.currency), i.credit, lead = Ic.src(i.src))
            } else {
                PassLine(i.title, i.sub(), lead = Ic.src(i.src), tone = Tone.Plain)
            }
        }
    }
}

@Composable
internal fun Input(q: String, set: (String) -> Unit, go: () -> Unit, listen: (() -> Unit)?, listening: Boolean, modifier: Modifier) {
    val p = pal
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(24.dp)).background(p.card)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
            if (q.isEmpty()) Text("Ask about your money", style = Ty.ui(20, FontWeight.Normal).copy(color = p.ink2))
            BasicTextField(
                q, set, modifier.fillMaxWidth().semantics { contentDescription = "Question" },
                textStyle = Ty.ui(20, FontWeight.Medium).copy(color = p.ink),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { go() }),
                maxLines = 3,
            )
        }
        if (q.isBlank()) {
            if (listen != null) Mic(listening, listen)
        } else {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(p.accent).clickable(role = Role.Button, onClickLabel = "Ask", onClick = go),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Ic.Send, "Ask", Modifier.size(22.dp), tint = p.onAccent)
            }
        }
    }
}

@Composable
private fun Mic(listening: Boolean, onClick: () -> Unit) {
    val p = pal
    val on = motion() && listening
    val b = if (on) rememberInfiniteTransition(label = "mic").animateFloat(1f, 1.35f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "b") else null
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        if (listening) {
            Box(
                Modifier.size(44.dp).graphicsLayer {
                    val s = b?.value ?: 1.15f
                    scaleX = s
                    scaleY = s
                    alpha = 1.4f - s
                }.border(2.dp, p.accent, CircleShape),
            )
        }
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(if (listening) p.accentBox else p.raised).clickable(role = Role.Button, onClickLabel = "Ask by voice", onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Ic.Mic, if (listening) "Listening" else "Ask by voice", Modifier.size(22.dp), tint = if (listening) p.onAccentBox else p.ink2)
        }
    }
}

@Composable
internal fun Thinking() {
    val p = pal
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(CardShape).background(p.card).rise()) {
        Row(Modifier.padding(start = 18.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.Sparkle, null, Modifier.size(18.dp), tint = p.accent)
            Text("Working it out on this phone", Modifier.padding(start = 8.dp), style = Ty.ui(13, FontWeight.Medium).copy(color = p.ink2))
        }
        Skel(lines = 2)
        Box(Modifier.width(1.dp).height(4.dp))
    }
}
