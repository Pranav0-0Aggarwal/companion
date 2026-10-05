package app.companion.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.core.Cycle
import app.companion.data.Item
import app.companion.data.Link
import app.companion.data.Profile
import app.companion.data.cardPay
import app.companion.data.moved
import app.companion.core.Flow as Route
import app.companion.core.Flows
import app.companion.data.credit
import app.companion.data.dueDate
import app.companion.data.money
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.Voice
import app.companion.ui.dateOf
import app.companion.ui.dayLabel
import app.companion.ui.daysTo
import app.companion.ui.inDays
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Ic
import app.companion.ui.kit.Ink
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Amount
import app.companion.ui.kit.StateStamp
import app.companion.ui.kit.SwipeAccept
import app.companion.ui.kit.Tone
import app.companion.ui.kit.motion
import app.companion.ui.kit.rememberHaptic
import app.companion.ui.pal
import app.companion.ui.rememberNow
import app.companion.ui.shortDay
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import app.companion.ui.money as amt

const val CRED = "com.dreamplug.androidapp"
private val names = mapOf("Sms" to "SMS", "Notif" to "app", "Mail" to "mail", "Wa" to "WhatsApp", "Ig" to "Instagram")

typealias Links = Map<Long, List<Link>>

class Day(
    val name: String,
    val hello: String,
    val sub: String,
    val today: LocalDate,
    val otps: List<Item>,
    val asks: List<Item>,
    val need: List<Item>,
    val bills: List<Item>,
    val printed: List<Item>,
    val links: Links,
)

fun String.cap() = replaceFirstChar(Char::uppercase)

fun Item.acct() = listOfNotNull(bank, last4?.let { "··$it" }).joinToString(" ").ifEmpty { null }

fun Item.meta() = listOfNotNull(category, acct(), currency.takeIf { it != "INR" && paise > 0 })

fun Item.via(links: Links) = links[id].orEmpty().map { it.src }.ifEmpty { listOf(src) }.distinct().joinToString(" + ") { names[it] ?: it }

fun Item.srcLine(links: Links) = (meta() + via(links)).joinToString(" · ")

fun Item.head() = if (money) "${amt(paise, currency)} · $title" else title

fun Item.picks() = if (money) listOfNotNull(category, "bills", "other").distinct().take(3) else listOf("file")

fun Item.stamp() = when {
    flow == Route.Self.name -> "OWN ACCOUNT"
    flow == Route.CardBill.name || cardPay -> "CARD PAYMENT"
    flow == Route.Invest.name -> "INVESTMENT"
    else -> null
}

fun List<Item>.tot(credit: Boolean) = filter { it.currency == "INR" && it.credit == credit && if (credit) Flows.income(it.flow) else !it.moved }.sumOf { it.paise }

fun List<Item>.away() = filter { it.currency == "INR" && !it.credit && it.moved }.sumOf { it.paise }

@Composable
fun rememberLinks(items: List<Item>): Links {
    val repo = LocalContext.current.sl.repo
    val ids = items.map { it.id }
    val l by remember(ids) { repo.links(ids).map { x -> x.groupBy { it.itemId } } }.collectAsStateWithLifecycle(emptyMap())
    return l
}

@Composable
fun rememberDay(): Day {
    val repo = LocalContext.current.sl.repo
    val prof by repo.profile.collectAsStateWithLifecycle(Profile())
    val otps by remember { repo.otps() }.collectAsStateWithLifecycle(emptyList())
    val asks by repo.asks.collectAsStateWithLifecycle(emptyList())
    val bills by repo.bills.collectAsStateWithLifecycle(emptyList())
    val money by repo.money.collectAsStateWithLifecycle(emptyList())
    val now by rememberNow(60_000)
    val day = dateOf(now)
    val need = remember(asks, bills, day) {
        (bills.filter { b -> b.dueDate?.let { daysTo(it, day) <= 7 } == true } + asks).distinctBy { it.id }
    }
    val printed = remember(money, day) { money.filter { dateOf(it.at) == day }.reversed() }
    val links = rememberLinks(need.take(5) + printed)
    val payday = prof.payDay?.let { "payday ${inDays(daysTo(Cycle.payday(it, day), day))}" }
    val sub = listOfNotNull(dayLabel(day), Voice.need(need.size).cap(), payday).joinToString(" · ")
    val hour = LocalTime.now(app.companion.ui.zone()).hour
    return Day(prof.name, Voice.hello(prof.name, hour), sub, day, otps, asks, need, bills, printed, links)
}

@Composable
fun NeedRow(i: Item, d: Day, onPaid: () -> Unit, onFile: (String) -> Unit) {
    if (i.bill) BillNeed(i, d.today, onPaid) else AskNeed(i, d.links, onFile)
}

private val Item.bill get() = kind == "Bill" || kind == "Statement"

@Composable
private fun rememberSettle(act: () -> Unit): Pair<Boolean, () -> Unit> {
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val on = motion()
    var done by remember { mutableStateOf(false) }
    return done to {
        if (!done) {
            done = true
            haptic(HapticFeedbackType.Confirm)
            scope.launch {
                delay(if (on) 560 else 120)
                act()
            }
        }
    }
}

@Composable
fun AskNeed(i: Item, links: Links, file: (String) -> Unit) {
    var pick by remember { mutableStateOf<String?>(null) }
    val (done, settle) = rememberSettle { pick?.let(file) }
    val choose = { c: String ->
        pick = c
        settle()
    }
    val first = i.picks().first()
    SwipeAccept("File as ${first.cap()}", { choose(first) }, enabled = !done) {
        PassLine(
            i.head(), i.srcLine(links), lead = Ic.of(i.category), tone = Tone.Accent, lines = 1, fill = true,
            trailing = { StateStamp(if (done) "SETTLED" else "ASK", if (done) Ink.Green else Ink.Red) },
            actions = { if (!done) i.picks().forEachIndexed { k, c -> Btn(c.cap(), Modifier.weight(1f), go = k == 0, dense = true) { choose(c) } } },
        )
    }
}

@Composable
fun BillNeed(i: Item, today: LocalDate, pay: () -> Unit) {
    val ctx = LocalContext.current
    val cred = remember { ctx.packageManager.getLaunchIntentForPackage(CRED) }
    val due = i.dueDate
    val days = due?.let { daysTo(it, today) }
    val (done, settle) = rememberSettle(pay)
    val sub = listOfNotNull(
        i.paise.takeIf { it > 0 }?.let { amt(it, i.currency) },
        i.minPaise?.let { "min ${amt(it, i.currency)}" },
        days?.let { inDays(it) },
    ).joinToString(" · ")
    PassLine(
        i.title, sub, lead = Ic.Bolt, tone = if ((days ?: 1) < 0) Tone.Red else Tone.Accent, lines = 1, fill = true,
        trailing = {
            val label = when {
                done -> "PAID"
                days != null && days < 0 -> "OVERDUE"
                due != null -> "DUE ${shortDay(due).uppercase()}"
                else -> "DUE"
            }
            StateStamp(label, if (done) Ink.Green else Ink.Red)
        },
        actions = {
            if (!done) {
                Btn("Mark paid", Modifier.weight(1f), go = true, dense = true, onClick = settle)
                if (cred != null) Btn("Pay with CRED", Modifier.weight(1f), dense = true) { ctx.startActivity(cred) }
            }
        },
    )
}

@Composable
fun SpendHead(items: List<Item>, modifier: Modifier = Modifier) {
    val p = pal
    val spent = items.tot(false)
    val inn = items.tot(true)
    val n = items.count { !it.credit && !it.moved }
    Column(modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
        Amount(amt(spent, "INR"), Ty.mono(40, FontWeight.Bold).copy(color = p.ink))
        val parts = listOfNotNull(
            if (n == 0) "No payments yet" else "$n payment${if (n == 1) "" else "s"}",
            if (inn > 0) "${amt(inn, "INR")} in" else null,
        )
        Text(parts.joinToString(" · "), Modifier.padding(top = 4.dp), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2))
    }
}
