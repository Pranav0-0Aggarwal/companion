package app.companion.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.core.Paid
import app.companion.data.Item
import app.companion.data.brand
import app.companion.data.Owed
import app.companion.data.Profile
import app.companion.data.dueDate
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.Voice
import app.companion.ui.dateOf
import app.companion.ui.daysTo
import app.companion.ui.inDays
import app.companion.ui.inr
import app.companion.ui.kit.Amount
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Ic
import app.companion.ui.kit.Ink
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Screen
import app.companion.ui.kit.Quiet
import app.companion.ui.kit.Section
import app.companion.ui.kit.StateStamp
import app.companion.ui.kit.TextBtn
import app.companion.ui.kit.ToolButton
import app.companion.ui.kit.Tone
import app.companion.ui.kit.empty
import app.companion.ui.kit.motion
import app.companion.ui.kit.part
import app.companion.ui.kit.rememberHaptic
import app.companion.ui.money
import app.companion.ui.pal
import app.companion.ui.shortDay
import app.companion.ui.today
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val titles = listOf("Overdue", "This week", "Later", "No date")

private fun bucket(b: Owed, day: LocalDate) = when (b.item.dueDate?.let { daysTo(it, day) }) {
    null -> 3
    in Int.MIN_VALUE..-1 -> 0
    in 0..7 -> 1
    else -> 2
}

@Composable
fun BillsScreen(go: (String) -> Unit) {
    val c = LocalContext.current
    val repo = c.sl.repo
    val scope = rememberCoroutineScope()
    val all by repo.owed.collectAsStateWithLifecycle(emptyList<Owed>())
    val paid by repo.paid.collectAsStateWithLifecycle(emptyList<Item>())
    val profile by repo.profile.collectAsStateWithLifecycle(Profile())
    val cred = remember { c.packageManager.getLaunchIntentForPackage(CRED) }
    val day = remember { today() }
    var older by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val (stale, bills) = remember(all) { all.partition { it.old } }
    val soon = bills.filter { b -> b.item.dueDate?.let { daysTo(it, day) <= 30 } == true }
    val sub = when {
        bills.isEmpty() -> "Nothing due"
        soon.isEmpty() -> "Nothing due in the next 30 days"
        else -> "${soon.size} due · ${inr(soon.sumOf { it.item.paise })} in the next 30 days"
    } + if (stale.isEmpty()) "" else " · ${stale.size} older to check"
    val groups = remember(bills, day) { bills.groupBy { bucket(it, day) } }
    val pay = { id: Long -> scope.launch { repo.pay(id) }; Unit }
    Screen("Bills", sub, tools = { ToolButton(Ic.Plan, "Plan and reminders") { go("plan") } }) {
        if (bills.isEmpty()) {
            val body = if (stale.isEmpty()) "nothing is waiting to be paid." else "nothing new is waiting. The older ones below are probably paid."
            empty(Ic.Bills, "You're all paid up", Voice.addr(profile.name, body))
        } else {
            titles.forEachIndexed { i, t ->
                groups[i]?.let { section(t, it, day, cred, pay) { intent -> c.startActivity(intent) } }
            }
        }
        if (stale.isNotEmpty()) {
            item(key = "hOlder") { Section("Older · ${stale.size}", if (older) "Hide" else "Show") { older = !older } }
            if (older) {
                item(key = "nOlder") { Quiet("Due a while ago with no newer bill after them. Probably paid.") }
                section("Older", stale, day, cred, pay, { id -> scope.launch { repo.keep(id) } }) { intent -> c.startActivity(intent) }
            }
        }
        if (paid.isNotEmpty()) {
            item(key = "hPaid") { Section("Recently paid", if (done) "Hide" else "Show") { done = !done } }
            if (done) {
                itemsIndexed(paid, key = { _, b -> "p${b.id}" }) { k, b ->
                    Box(Modifier.animateItem().part(pal, k == 0, k == paid.lastIndex)) { PaidLine(b) }
                }
            }
        }
    }
}

private fun how(b: Item): String {
    val n = Paid.read(b.note) ?: return "Marked paid"
    val d = shortDay(dateOf(n.at))
    return when (n.by) {
        Paid.PAY -> "Paid · ${n.name ?: "Card"} payment on $d"
        Paid.NEWER -> "Closed · a newer bill came on $d"
        else -> "Marked paid on $d"
    }
}

@Composable
private fun PaidLine(b: Item) {
    val p = pal
    val card = listOfNotNull(b.bank, b.last4?.let { "··$it" }).joinToString(" ")
    PassLine(
        b.title,
        listOf(how(b), card).filter { it.isNotEmpty() }.joinToString(" · "),
        brand = b.brand,
        tone = Tone.Green,
        trailing = { Amount(money(b.paise, b.currency), Ty.mono(16, FontWeight.SemiBold).copy(color = p.ink2), Modifier.widthIn(max = 148.dp), roll = false) },
    )
}

private fun LazyListScope.section(title: String, list: List<Owed>, day: LocalDate, cred: Intent?, onPay: (Long) -> Unit, onKeep: ((Long) -> Unit)? = null, onCred: (Intent) -> Unit) {
    if (onKeep == null) item(key = "h$title") { Section(title) }
    itemsIndexed(list, key = { _, b -> "b${b.item.id}" }) { k, b ->
        Box(Modifier.animateItem().part(pal, k == 0, k == list.lastIndex)) {
            BillLine(b, day, cred, { onPay(b.item.id) }, onKeep?.let { f -> { f(b.item.id) } }, onCred)
        }
    }
}

@Composable
private fun BillLine(o: Owed, day: LocalDate, cred: Intent?, onPay: () -> Unit, onKeep: (() -> Unit)?, onCred: (Intent) -> Unit) {
    val b = o.item
    val p = pal
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val on = motion()
    var paid by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    val due = b.dueDate
    val days = due?.let { daysTo(it, day) }
    val card = listOfNotNull(b.bank, b.last4?.let { "··$it" }).joinToString(" ").ifEmpty { null }
    val sub = listOfNotNull(if (o.old) due?.let { "Due ${shortDay(it)}" } else days?.let { inDays(it).replaceFirstChar(Char::uppercase) }, card, b.minPaise?.let { "min ${money(it, b.currency)}" }, o.n.takeIf { it > 1 }?.let { "$it messages" }).joinToString(" · ")
    PassLine(
        b.title,
        sub,
        lead = Ic.Bolt,
        brand = b.brand,
        lines = 3,
        tone = if (o.old) Tone.Plain else if ((days ?: 1) < 0) Tone.Red else Tone.Accent,
        onLong = {
            haptic(HapticFeedbackType.LongPress)
            sheet = true
        },
        longLabel = "Mark as duplicate",
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Amount(money(b.paise, b.currency), Ty.mono(16, FontWeight.SemiBold).copy(color = p.ink), Modifier.widthIn(max = 148.dp), roll = false)
                val label = when {
                    paid -> "PAID"
                    o.old -> "PROBABLY PAID"
                    days != null && days < 0 -> "OVERDUE"
                    due != null -> "DUE ${shortDay(due).uppercase()}"
                    else -> null
                }
                if (label != null) StateStamp(label, if (paid) Ink.Green else if (o.old) Ink.Quiet else Ink.Red, Modifier.padding(top = 8.dp))
            }
        },
        actions = {
            if (!paid) {
                Btn("Mark paid", go = true) {
                    paid = true
                    haptic(HapticFeedbackType.Confirm)
                    scope.launch {
                        delay(if (on) 560 else 120)
                        onPay()
                    }
                }
                if (onKeep != null) {
                    TextBtn("Still due", color = p.ink2, onClick = onKeep)
                } else {
                    if (cred != null) Btn("Pay with CRED") { onCred(cred) }
                    if (due != null) RemindBtn(b.title, due)
                }
            }
        },
    )
    if (sheet) DupSheet(b) { sheet = false }
}
