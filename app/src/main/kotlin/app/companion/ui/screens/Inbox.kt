package app.companion.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.data.Item
import app.companion.data.Profile
import app.companion.data.State
import app.companion.data.Tally
import app.companion.data.cal
import app.companion.data.money
import app.companion.data.tagList
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.Voice
import app.companion.ui.clock
import app.companion.ui.dateOf
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Chip
import app.companion.ui.kit.Ic
import app.companion.ui.kit.Ink
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Screen
import app.companion.ui.kit.Section
import app.companion.ui.kit.StateStamp
import app.companion.ui.kit.SwipeAccept
import app.companion.ui.kit.TextBtn
import app.companion.ui.kit.Tone
import app.companion.ui.kit.empty
import app.companion.ui.kit.motion
import app.companion.ui.kit.part
import app.companion.ui.kit.rememberHaptic
import app.companion.ui.money
import app.companion.ui.pal
import app.companion.ui.shortDay
import app.companion.ui.today
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class InboxSeg(val label: String, val src: Set<String>) {
    All("All", emptySet()),
    Mail("Mail", setOf("Mail")),
    WhatsApp("WhatsApp", setOf("Wa")),
    Instagram("Instagram", setOf("Ig")),
    Alerts("Alerts", setOf("Sms", "Notif")),
}

private fun folded(t: List<Tally>): String? {
    val n = t.associate { it.key to it.n }
    val parts = listOf("Wa", "Ig", "Sms", "Promo", "Other").mapNotNull { k ->
        n[k]?.takeIf { it > 0 }?.let { c ->
            when (k) {
                "Wa" -> "$c WhatsApp"
                "Ig" -> "$c Instagram"
                "Sms" -> "$c SMS"
                "Promo" -> "$c ${if (c == 1) "promo" else "promos"}"
                else -> "$c other"
            }
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(", ", postfix = " folded today")
}

private fun stamped(ms: Long) = dateOf(ms).let { if (it == today()) clock(ms) else "${shortDay(it)} ${clock(ms)}" }

private fun cats(i: Item) = listOfNotNull(i.category, "bills", "other").distinct()

private val Item.open get() = state == State.ASK || state == State.CHECK

internal class Acts(val file: (Item, String) -> Unit, val done: (Item) -> Unit, val spam: (Item) -> Unit, val notSpam: (Item) -> Unit)

@Composable
fun InboxScreen(go: (String) -> Unit) {
    val p = pal
    val c = LocalContext.current
    val repo = c.sl.repo
    val scope = rememberCoroutineScope()
    val items by remember { repo.inbox(System.currentTimeMillis() - 3.days.inWholeMilliseconds) }.collectAsStateWithLifecycle(emptyList<Item>())
    val tally by remember { repo.tally(today().toEpochDay()) }.collectAsStateWithLifecycle(emptyList<Tally>())
    val profile by repo.profile.collectAsStateWithLifecycle(Profile())
    var seg by rememberSaveable { mutableStateOf(InboxSeg.All) }
    val shown = remember(items, seg) { items.filter { seg == InboxSeg.All || it.src in seg.src || (seg == InboxSeg.Alerts && it.kind == "Spam") } }
    val (waiting, filed) = remember(shown) { shown.partition { it.open } }
    val asks = items.count { it.state == State.ASK }
    val checks = items.count { it.state == State.CHECK }
    val sub = listOfNotNull(
        asks.takeIf { it > 0 }?.let { "$it to file" },
        checks.takeIf { it > 0 }?.let { "$it to check" },
    ).joinToString(" · ").ifEmpty { "Nothing waiting" }
    val acts = remember(repo) {
        Acts(
            { i, cat -> scope.launch { repo.file(i.id, cat) } },
            { i -> scope.launch { if (i.state == State.ASK) repo.confirm(i.id) else repo.dismiss(i.id) } },
            { i -> scope.launch { repo.spam(i.id) } },
            { i -> scope.launch { repo.notSpam(i.id) } },
        )
    }
    Screen("Inbox", sub) {
        item(key = "seg") {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InboxSeg.entries.forEach { s -> Chip(s.label, seg == s) { seg = s } }
            }
        }
        if (shown.isEmpty()) {
            empty(Ic.Inbox, if (seg == InboxSeg.All) "Inbox is clear" else "Nothing in ${seg.label}", Voice.addr(profile.name, "nothing needs you here."))
        }
        group("Needs you", waiting, acts)
        group("Filed", filed, acts)
        folded(tally)?.let { f ->
            item(key = "folded") { Text(f, Modifier.padding(horizontal = 28.dp, vertical = 16.dp), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2)) }
        }
    }
}

private fun LazyListScope.group(title: String, list: List<Item>, acts: Acts) {
    if (list.isEmpty()) return
    item(key = "g$title") { Section(title) }
    itemsIndexed(list, key = { _, i -> "i${i.id}" }) { k, i ->
        Box(Modifier.animateItem().part(pal, k == 0, k == list.lastIndex)) { Entry(i, acts) }
    }
}

@Composable
internal fun Entry(i: Item, a: Acts) {
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val on = motion()
    var settled by remember(i.id, i.state) { mutableStateOf(false) }
    val act = { f: () -> Unit ->
        settled = true
        haptic(HapticFeedbackType.Confirm)
        scope.launch {
            delay(if (on) 480 else 100)
            f()
        }
        Unit
    }
    val first = if (i.money) cats(i).first() else null
    val swipe = i.state == State.ASK && i.kind != "Spam" && i.kind != "Promo"
    SwipeAccept(if (first != null) "File as ${first.cap()}" else "File", { act { if (first != null) a.file(i, first) else a.done(i) } }, enabled = swipe && !settled) {
        PassLine(
            i.title,
            listOfNotNull(srcWord(i.src), stamped(i.at), i.takeIf { it.paise > 0 }?.let { money(it.paise, it.currency) }, i.note.take(80).ifBlank { null }).joinToString(" · "),
            tags = i.tagList,
            lead = Ic.src(i.src),
            tone = if (i.open) Tone.Accent else Tone.Plain,
            trailing = {
                val (t, ink) = when {
                    i.kind == "Spam" -> "SPAM" to Ink.Quiet
                    settled || !i.open -> "SETTLED" to Ink.Green
                    i.state == State.CHECK -> "CHECK" to Ink.Amber
                    else -> "ASK" to Ink.Red
                }
                StateStamp(t, ink)
            },
            actions = when {
                i.kind == "Promo" || i.kind == "Spam" -> {
                    { TextBtn("Not spam") { a.notSpam(i) } }
                }
                i.open && !settled -> {
                    { Buttons(i, a, act) }
                }
                else -> null
            },
        )
    }
}

@Composable
private fun RowScope.Buttons(i: Item, a: Acts, act: (() -> Unit) -> Unit) {
    when {
        i.state == State.CHECK -> Btn("Done", go = true) { act { a.done(i) } }
        i.money -> cats(i).forEachIndexed { n, cat -> Btn(cat.cap(), go = n == 0) { act { a.file(i, cat) } } }
        else -> {
            Btn("File", go = true) { act { a.done(i) } }
            TextBtn("Spam") { a.spam(i) }
        }
    }
    i.cal()?.let { CalBtn(it) }
}
