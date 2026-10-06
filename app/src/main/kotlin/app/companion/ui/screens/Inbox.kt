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
import androidx.compose.material3.Icon
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
import app.companion.core.Alerts
import app.companion.data.Item
import app.companion.data.brand
import app.companion.data.Profile
import app.companion.data.State
import app.companion.data.Taught
import app.companion.data.Tally
import app.companion.data.cal
import app.companion.data.money
import app.companion.data.tagList
import app.companion.sl
import app.companion.ingest.NotifService
import app.companion.ui.Ty
import app.companion.ui.Voice
import app.companion.ui.clock
import app.companion.ui.dateOf
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Chip
import app.companion.ui.kit.Ic
import app.companion.ui.kit.Ink
import app.companion.ui.kit.LocalSnack
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
import java.time.ZoneId
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
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val items by remember { repo.inbox(System.currentTimeMillis() - 3.days.inWholeMilliseconds) }.collectAsStateWithLifecycle(emptyList<Item>())
    val tally by remember { repo.tally(today().toEpochDay()) }.collectAsStateWithLifecycle(emptyList<Tally>())
    val chatSince = remember { Alerts.dayStart(System.currentTimeMillis(), ZoneId.systemDefault()) }
    val chats by remember { repo.chats(chatSince) }.collectAsStateWithLifecycle(emptyList<Item>())
    val profile by repo.profile.collectAsStateWithLifecycle(Profile())
    val links by remember(items) { repo.links(items.map { it.id }) }.collectAsStateWithLifecycle(emptyList())
    val from = remember(links) { links.groupBy { it.itemId }.mapValues { it.value.first().sender } }
    var seg by rememberSaveable { mutableStateOf(InboxSeg.All) }
    val shown = remember(items, seg) { items.filter { seg == InboxSeg.All || it.src in seg.src || (seg == InboxSeg.Alerts && it.kind == "Spam") } }
    val (waiting, filed) = remember(shown) { shown.partition { it.open } }
    val quiet = remember(chats, seg) { chats.filter { seg == InboxSeg.All || it.src in seg.src } }
    var chatsOpen by rememberSaveable { mutableStateOf(false) }
    val asks = items.count { it.state == State.ASK }
    val checks = items.count { it.state == State.CHECK }
    val sub = listOfNotNull(
        asks.takeIf { it > 0 }?.let { "$it to file" },
        checks.takeIf { it > 0 }?.let { "$it to check" },
        chats.size.takeIf { it > 0 }?.let { "$it ${if (it == 1) "chat" else "chats"}" },
    ).joinToString(" · ").ifEmpty { "Nothing waiting" }
    val acts = remember(repo, snack) {
        Acts(
            { i, cat -> snack.teach(repo) { repo.file(i.id, cat) } },
            { i -> snack.teach(repo) { if (i.state == State.ASK) repo.confirm(i.id) else Taught().also { repo.dismiss(i.id) } } },
            { i -> snack.teach(repo) { repo.spam(i.id) } },
            { i -> snack.teach(repo) { repo.notSpam(i.id) } },
        )
    }
    Screen("Inbox", sub, tall = false) {
        item(key = "seg") {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InboxSeg.entries.forEach { s -> Chip(s.label, seg == s) { seg = s } }
            }
        }
        item(key = "hear") { Hearing() }
        val notifs = waiting.filter { it.src == "Notif" }
        if (notifs.isNotEmpty()) item(key = "notifs") {
            TextBtn("Dismiss ${notifs.size} app ${if (notifs.size == 1) "notification" else "notifications"}", Modifier.padding(horizontal = 16.dp)) {
                val titles = notifs.associate { it.id to it.title }
                NotifService.clear(links.filter { it.src == "Notif" && it.itemId in titles }.map { Triple(it.sender, it.at, titles.getValue(it.itemId)) })
                snack.go { repo.settleAll(notifs)?.let { a -> snack.offer(a, "Dismissed ${a.n}") { repo.undo(it) } } }
            }
        }
        if (asks + checks > 20) item(key = "tidy") {
            TextBtn("Clean up ${asks + checks} waiting", Modifier.padding(horizontal = 16.dp)) {
                snack.go { repo.tidy(System.currentTimeMillis())?.let { a -> snack.offer(a, "Cleaned up ${a.n}") { repo.undo(it) } } ?: snack.say("Nothing safe to clean up") }
            }
        }
        if (shown.isEmpty() && quiet.isEmpty()) {
            val (t, b) = if (seg == InboxSeg.All) "All clear" to "you're all caught up. Anything that needs a look will wait for you here." else "Nothing in ${seg.label}" to "no ${seg.label} messages need a look right now."
            empty(Ic.Inbox, t, Voice.addr(profile.name, b))
        }
        group("Needs you", waiting, acts, from)
        group("Filed", filed, acts, from)
        chats(quiet, chatsOpen, { chatsOpen = !chatsOpen }) { n -> scope.launch { repo.important(n) } }
        folded(tally)?.let { f ->
            item(key = "folded") { Text(f, Modifier.padding(horizontal = 28.dp, vertical = 16.dp), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2)) }
        }
    }
}

private fun LazyListScope.chats(list: List<Item>, open: Boolean, toggle: () -> Unit, important: (String) -> Unit) {
    if (list.isEmpty()) return
    val people = list.groupBy { it.title }.values.toList()
    item(key = "chats") {
        Box(Modifier.animateItem().padding(top = 16.dp).part(pal, true, !open)) {
            PassLine(
                "${list.size} ${if (list.size == 1) "chat" else "chats"} today · not important",
                "${people.size} ${if (people.size == 1) "person" else "people"}, kept quiet",
                lead = Ic.Chat,
                tone = Tone.Plain,
                onClick = toggle,
                trailing = { Icon(if (open) Ic.Down else Ic.Right, null, tint = pal.ink2) },
            )
        }
    }
    if (!open) return
    itemsIndexed(people, key = { _, l -> "c${l.first().title}" }) { k, l ->
        val last = l.first()
        Box(Modifier.animateItem().part(pal, false, k == people.lastIndex)) {
            PassLine(
                last.title,
                listOfNotNull("${l.size} ${if (l.size == 1) "message" else "messages"}", stamped(last.at), last.body?.take(80)).joinToString(" · "),
                lead = Ic.src(last.src),
                tone = Tone.Plain,
                actions = { TextBtn("Mark important") { important(last.title) } },
            )
        }
    }
}

private fun LazyListScope.group(title: String, list: List<Item>, acts: Acts, from: Map<Long, String>) {
    if (list.isEmpty()) return
    item(key = "g$title") { Section(title) }
    itemsIndexed(list, key = { _, i -> "i${i.id}" }) { k, i ->
        Box(Modifier.animateItem().part(pal, k == 0, k == list.lastIndex)) { Entry(i, acts, from[i.id]) }
    }
}

@Composable
internal fun Entry(i: Item, a: Acts, sender: String? = null) {
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
    var sheet by remember { mutableStateOf(false) }
    val first = if (i.money) cats(i).first() else null
    val swipe = i.state == State.ASK && i.kind != "Spam" && i.kind != "Promo"
    SwipeAccept(if (first != null) "File as ${first.cap()}" else "File", { act { if (first != null) a.file(i, first) else a.done(i) } }, enabled = swipe && !settled) {
        PassLine(
            i.title,
            listOfNotNull(sender?.takeIf { i.src == "Sms" && it != i.title && !i.money } ?: srcWord(i.src), stamped(i.at), i.takeIf { it.paise > 0 }?.let { money(it.paise, it.currency) }, i.note.take(80).ifBlank { null }).joinToString(" · "),
            tags = i.tagList,
            onClick = { sheet = true },
            lead = Ic.src(i.src),
            brand = i.brand,
            tone = if (i.open) Tone.Accent else Tone.Plain,
            trailing = {
                val (t, ink) = when {
                    i.kind == "Spam" -> "SPAM" to Ink.Quiet
                    settled || !i.open -> "SETTLED" to Ink.Green
                    i.state == State.CHECK -> "CHECK" to Ink.Quiet
                    else -> "ASK" to Ink.Red
                }
                StateStamp(t, ink)
            },
            actions = when {
                i.kind == "Promo" || i.kind == "Spam" -> {
                    {
                        TextBtn("Not spam") { a.notSpam(i) }
                        TextBtn("This is…") { sheet = true }
                    }
                }
                i.open && !settled -> {
                    { Buttons(i, a, act) { sheet = true } }
                }
                else -> null
            },
        )
    }
    if (sheet) TypeSheet(i) { sheet = false }
}

@Composable
private fun RowScope.Buttons(i: Item, a: Acts, act: (() -> Unit) -> Unit, sheet: () -> Unit) {
    when {
        i.state == State.CHECK -> Btn("Done", go = true) { act { a.done(i) } }
        i.money -> cats(i).first().let { cat -> Btn(cat.cap(), go = true) { act { a.file(i, cat) } } }
        else -> {
            Btn("File", go = true) { act { a.done(i) } }
            TextBtn("Spam") { a.spam(i) }
        }
    }
    TextBtn("This is…", color = pal.ink2) { sheet() }
    i.cal()?.let { CalBtn(it) }
}
