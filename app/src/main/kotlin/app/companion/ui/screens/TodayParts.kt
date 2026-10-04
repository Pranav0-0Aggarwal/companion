package app.companion.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.core.Cycle
import app.companion.data.Item
import app.companion.data.Link
import app.companion.data.Profile
import app.companion.data.credit
import app.companion.data.dueDate
import app.companion.data.money
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.Voice
import app.companion.ui.clock
import app.companion.ui.codeText
import app.companion.ui.copy
import app.companion.ui.dateOf
import app.companion.ui.daysTo
import app.companion.ui.inDays
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Coupon
import app.companion.ui.kit.LedgerHead
import app.companion.ui.kit.LedgerRow
import app.companion.ui.kit.LedgerTotal
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Stamp
import app.companion.ui.left
import app.companion.ui.pal
import app.companion.ui.plain
import app.companion.ui.rememberNow
import app.companion.ui.shortDay
import java.time.LocalDate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import app.companion.ui.money as amt

private const val CRED = "com.dreamplug.androidapp"
private val names = mapOf("Sms" to "SMS", "Notif" to "app", "Mail" to "mail", "Wa" to "WhatsApp", "Ig" to "Instagram")

typealias Links = Map<Long, List<Link>>

class Day(
    val name: String,
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

fun List<Item>.tot(credit: Boolean) = filter { it.currency == "INR" && it.credit == credit }.sumOf { it.paise }

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
    val links = rememberLinks(need.take(3) + asks.take(1) + printed)
    val payday = prof.payDay?.let { "payday ${inDays(daysTo(Cycle.payday(it, day), day))}" }
    val sub = listOfNotNull(Voice.greet(prof.name, need.size), payday).joinToString(" · ")
    return Day(prof.name, sub, day, otps, asks, need, bills, printed, links)
}

@Composable
fun Quiet(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = Ty.ui(14, FontWeight.Normal).copy(color = pal.ink2))
}

@Composable
fun Otps(items: List<Item>) {
    val ctx = LocalContext.current
    val repo = ctx.sl.repo
    val scope = rememberCoroutineScope()
    val now by rememberNow()
    var copied by remember { mutableLongStateOf(-1L) }
    val scroll = rememberScrollState()
    val live = items.filter { (it.expires ?: 0) > now }
    if (live.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = if (live.size <= 2) (maxWidth - 32.dp - 10.dp * (live.size - 1)) / live.size else 176.dp
        Row(
            Modifier.horizontalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            live.forEach { o ->
                val end = o.expires ?: 0
                Column {
                    Coupon(
                        service = o.title,
                        code = codeText(o.code.orEmpty()),
                        meta = if (copied == o.id) "copied" else "${o.note.ifBlank { "code" }} · tap to copy",
                        timeLeft = "${left(end - now)} LEFT",
                        remaining = (end - now).toFloat() / (end - o.at).coerceAtLeast(1),
                        modifier = Modifier.width(w),
                    ) {
                        copy(ctx, o.code.orEmpty())
                        copied = o.id
                    }
                    Text(
                        "Not an OTP",
                        Modifier.padding(top = 6.dp, start = 4.dp).clickable(role = Role.Button) { scope.launch { repo.notOtp(o.id) } },
                        style = Ty.mono(11).copy(color = pal.ink2),
                    )
                }
            }
        }
    }
}

@Composable
fun AskLine(i: Item, links: Links, file: (String) -> Unit) {
    PassLine(i.head(), i.srcLine(links), trailing = { Stamp("ASK", thump = true) }, actions = {
        i.picks().forEach { c -> Btn(c.cap()) { file(c) } }
    })
}

@Composable
fun BillLine(i: Item, today: LocalDate, pay: () -> Unit) {
    val ctx = LocalContext.current
    val cred = remember { ctx.packageManager.getLaunchIntentForPackage(CRED) }
    val due = i.dueDate
    val sub = listOfNotNull(
        i.paise.takeIf { it > 0 }?.let { amt(it, i.currency) },
        i.minPaise?.let { "min ${amt(it, i.currency)}" },
        due?.let { inDays(daysTo(it, today)) },
    ).joinToString(" · ")
    PassLine(i.title, sub, trailing = { if (due != null) Stamp("DUE ${shortDay(due).uppercase()}") }, actions = {
        if (cred != null) Btn("Pay with CRED", go = true) { ctx.startActivity(cred) }
        Btn("Mark paid", onClick = pay)
    })
}

@Composable
fun TodayLines(items: List<Item>, links: Links) {
    val repo = LocalContext.current.sl.repo
    if (items.isEmpty()) {
        Quiet("Nothing printed yet today")
        return
    }
    Column {
        LedgerHead("TIME")
        items.forEach { i ->
            val fresh = repo.fresh[i.id]?.let { System.currentTimeMillis() - it < 30_000 } == true
            LedgerRow(
                clock(i.at), i.title, i.srcLine(links),
                if (i.credit) "" else plain(i.paise), if (i.credit) plain(i.paise) else "",
                printing = fresh,
            )
        }
        LedgerTotal("Today", plain(items.tot(false)), plain(items.tot(true)))
    }
}
