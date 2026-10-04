package app.companion.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.data.Item
import app.companion.data.cal
import app.companion.data.Profile
import app.companion.data.State
import app.companion.data.Tally
import app.companion.data.money
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.Voice
import app.companion.ui.clock
import app.companion.ui.dateOf
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Chip
import app.companion.ui.kit.Group
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Screen
import app.companion.ui.kit.Stamp
import app.companion.ui.money
import app.companion.ui.pal
import app.companion.ui.shortDay
import app.companion.ui.today
import kotlin.time.Duration.Companion.days
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

@Composable
fun InboxScreen(go: (String) -> Unit) {
    val c = LocalContext.current
    val repo = c.sl.repo
    val scope = rememberCoroutineScope()
    val items by remember { repo.inbox(System.currentTimeMillis() - 3.days.inWholeMilliseconds) }.collectAsStateWithLifecycle(emptyList<Item>())
    val tally by remember { repo.tally(today().toEpochDay()) }.collectAsStateWithLifecycle(emptyList<Tally>())
    val profile by repo.profile.collectAsStateWithLifecycle(Profile())
    var seg by remember { mutableStateOf(InboxSeg.All) }
    val shown = items.filter { seg == InboxSeg.All || it.src in seg.src }
    val asks = items.count { it.state == State.ASK }
    val checks = items.count { it.state == State.CHECK }
    val sub = listOfNotNull(
        asks.takeIf { it > 0 }?.let { "$it to file" },
        checks.takeIf { it > 0 }?.let { "$it to check" },
    ).joinToString(" · ").ifEmpty { "Nothing waiting" }
    Screen("Inbox", sub) {
        item(key = "seg") {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InboxSeg.entries.forEach { s -> Chip(s.label, seg == s) { seg = s } }
            }
        }
        if (shown.isEmpty()) {
            emptyPage(if (seg == InboxSeg.All) "Inbox is clear" else "Nothing in ${seg.label}", Voice.addr(profile.name, "nothing needs you here"))
        }
        items(shown, key = { "i${it.id}" }) { i ->
            Group(Modifier.animateItem().padding(bottom = 8.dp)) {
                PassLine(
                    i.title,
                    listOfNotNull(srcWord(i.src), stamped(i.at), i.takeIf { it.paise > 0 }?.let { money(it.paise, it.currency) }, i.note.take(80).ifBlank { null }).joinToString(" · "),
                    trailing = { InboxStamp(i.state) },
                    actions = when {
                        i.kind == "Promo" -> {
                            { Btn("Not spam") { scope.launch { repo.notSpam(i.id) } } }
                        }
                        i.state == State.ASK || i.state == State.CHECK -> {
                            {
                                Acts(
                                    i,
                                    { cat -> scope.launch { repo.file(i.id, cat) } },
                                    { scope.launch { if (i.state == State.ASK) repo.confirm(i.id) else repo.dismiss(i.id) } },
                                ) { scope.launch { repo.spam(i.id) } }
                            }
                        }
                        else -> null
                    },
                )
            }
        }
        folded(tally)?.let { f ->
            item(key = "folded") { Text(f, Modifier.padding(horizontal = 20.dp, vertical = 16.dp), style = Ty.mono(12).copy(color = pal.ink2)) }
        }
    }
}

@Composable
private fun InboxStamp(state: String) {
    val first = remember { state }
    AnimatedContent(state, label = "stamp") { s ->
        when (s) {
            State.ASK -> Stamp("ASK", thump = true)
            State.CHECK -> Stamp("CHECK")
            else -> Stamp("SETTLED", ok = true, thump = s != first)
        }
    }
}

@Composable
private fun RowScope.Acts(i: Item, onFile: (String) -> Unit, onDone: () -> Unit, onSpam: () -> Unit) {
    when {
        i.state == State.CHECK -> Btn("Done", onClick = onDone)
        i.money -> cats(i).forEachIndexed { n, cat -> Btn(cat.replaceFirstChar(Char::uppercase), go = n == 0) { onFile(cat) } }
        else -> {
            Btn("File", go = true, onClick = onDone)
            Btn("Spam", onClick = onSpam)
        }
    }
    i.cal()?.let { CalBtn(it) }
}
