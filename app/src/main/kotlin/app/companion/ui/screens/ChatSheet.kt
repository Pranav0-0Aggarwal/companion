package app.companion.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.companion.chat.LogReq
import app.companion.chat.Msg
import app.companion.core.Card
import app.companion.core.Opt
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.dayLabel
import app.companion.ui.kit.Chip
import app.companion.ui.kit.Empty
import app.companion.ui.kit.Ic
import app.companion.ui.kit.ToolButton
import app.companion.ui.pal
import app.companion.ui.rememberVoice
import app.companion.ui.today
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val chips = listOf("Log lunch", "Food today", "Spent this week", "Next bill")

@Composable
fun ChatSheet(r: AskReq, open: Boolean, close: () -> Unit, go: (String) -> Unit, drag: Animatable<Float, *>) {
    val p = pal
    val s = LocalContext.current.sl.session
    val scope = rememberCoroutineScope()
    val d = LocalDensity.current
    val kb = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var q by remember(r) { mutableStateOf(r.text) }
    var hint by remember(r) { mutableStateOf("Ask or say what you ate") }
    var listening by remember { mutableStateOf(false) }
    val leave = { route: String ->
        close()
        go(route)
    }
    val submit = { t: String ->
        if (t.isNotBlank() && !s.busy) {
            q = ""
            hint = "Ask or say what you ate"
            kb?.hide()
            s.send(t, leave)
        }
    }
    val voice = rememberVoice { t ->
        listening = false
        t?.let(submit)
    }
    val listen = voice?.let { v -> { listening = true; v() } }
    val log = { c: LogReq, text: String ->
        s.ctx = c
        q = text
        hint = "What did you have?"
        runCatching { focus.requestFocus() }
        Unit
    }
    LaunchedEffect(r) {
        r.log?.let { s.ctx = it }
        if (r.voice && listen != null) {
            listen()
        } else if (r.text.isNotBlank() || s.msgs.isEmpty()) {
            delay(220)
            runCatching { focus.requestFocus() }
        }
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
            Text("Companion", Modifier.weight(1f), style = Ty.ui(22, FontWeight.Bold).copy(color = p.ink))
            if (s.msgs.isNotEmpty()) ToolButton(Ic.Edit, "New chat") { s.reset(); q = "" }
            ToolButton(Ic.Close, "Close", close)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (s.msgs.isEmpty() && !s.typing) {
                Empty(Ic.Sparkle, "Ask or log anything", "Spending, bills, plans and meals. It all stays on this phone.", Modifier.align(Alignment.Center))
            } else {
                LazyColumn(Modifier.fillMaxSize(), reverseLayout = true, contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (s.typing) item(key = "typing") { Typing() }
                    items(s.msgs.asReversed(), key = { it.id }) { m ->
                        when (m) {
                            is Msg.User -> UserBubble(m.text)
                            is Msg.Bot -> BotBubble(m.text)
                            is Msg.Rule -> AnswerCard(m.answer, leave)
                            is Msg.Res -> ResCard(m.card, leave) { c -> log(LogReq(java.time.LocalDate.ofEpochDay(c.day), c.slot, c.id), "I had ") }
                            is Msg.Pick -> PickCard(m) { o: Opt ->
                                val t = o.send
                                if (t != null) submit(t) else {
                                    hint = "Calories, like 320"
                                    runCatching { focus.requestFocus() }
                                }
                            }
                            is Msg.Hits -> HitsCard(m.items, leave)
                            is Msg.Need -> NeedCard()
                            is Msg.Note -> BotBubble(m.text)
                        }
                    }
                }
            }
        }
        Column(Modifier.navigationBarsPadding().imePadding()) {
            s.ctx?.let { c ->
                Row(
                    Modifier.padding(start = 24.dp, bottom = 4.dp).clip(RoundedCornerShape(18.dp)).background(p.accentBox)
                        .clickable(role = Role.Button, onClickLabel = "Stop logging a meal") { s.ctx = null }.padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Logging ${c.slot ?: "a meal"} · ${if (c.day == today()) "today" else dayLabel(c.day)}", style = Ty.ui(13).copy(color = p.onAccentBox))
                    Icon(Ic.Close, null, Modifier.padding(start = 8.dp).size(14.dp), tint = p.onAccentBox)
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(chips) { c ->
                    Chip(c, false) {
                        when (c) {
                            "Log lunch" -> log(LogReq(today(), "lunch"), "I had ")
                            "Food today" -> s.today()
                            "Next bill" -> submit("Bills due this week")
                            else -> submit(c)
                        }
                    }
                }
            }
            ChatInput(q, { q = it }, { submit(q) }, listen, listening, s.busy, { s.stop() }, hint, Modifier.focusRequester(focus))
        }
    }
}
