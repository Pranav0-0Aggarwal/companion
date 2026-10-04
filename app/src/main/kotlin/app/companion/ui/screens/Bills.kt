package app.companion.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.data.Item
import app.companion.data.Profile
import app.companion.data.dueDate
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.Voice
import app.companion.ui.daysTo
import app.companion.ui.inDays
import app.companion.ui.inr
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Group
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Rule
import app.companion.ui.kit.Screen
import app.companion.ui.kit.Section
import app.companion.ui.kit.Stamp
import app.companion.ui.money
import app.companion.ui.pal
import app.companion.ui.shortDay
import app.companion.ui.today
import java.time.LocalDate
import kotlinx.coroutines.launch

private const val CRED = "com.dreamplug.androidapp"
private val titles = listOf("Overdue", "This week", "Later", "No date")

private fun bucket(b: Item, day: LocalDate) = when (b.dueDate?.let { daysTo(it, day) }) {
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
    val bills by repo.bills.collectAsStateWithLifecycle(emptyList<Item>())
    val profile by repo.profile.collectAsStateWithLifecycle(Profile())
    val cred = remember { c.packageManager.getLaunchIntentForPackage(CRED) }
    val day = remember { today() }
    val soon = bills.filter { b -> b.dueDate?.let { daysTo(it, day) <= 30 } == true }
    val sub = if (bills.isEmpty()) "Nothing due" else "${soon.size} due · ${inr(soon.sumOf { it.paise })} in the next 30 days"
    Screen("Bills", sub) {
        if (bills.isEmpty()) {
            emptyPage("No bills due", Voice.addr(profile.name, "nothing is waiting to be paid"))
        } else {
            val groups = bills.groupBy { bucket(it, day) }
            titles.forEachIndexed { i, t ->
                groups[i]?.let { section(t, it, day, cred, { id -> scope.launch { repo.pay(id) } }) { intent -> c.startActivity(intent) } }
            }
        }
    }
}

private fun LazyListScope.section(title: String, list: List<Item>, day: LocalDate, cred: Intent?, onPay: (Long) -> Unit, onCred: (Intent) -> Unit) {
    item(key = "h$title") { Section(title) }
    item(key = "g$title") {
        Group {
            list.forEachIndexed { i, b ->
                if (i > 0) Rule()
                BillLine(b, day, cred, { onPay(b.id) }, onCred)
            }
        }
    }
}

@Composable
private fun BillLine(b: Item, day: LocalDate, cred: Intent?, onPay: () -> Unit, onCred: (Intent) -> Unit) {
    val p = pal
    val due = b.dueDate
    val card = listOfNotNull(b.bank, b.last4?.let { "··$it" }).joinToString(" ").ifEmpty { null }
    val sub = listOfNotNull(due?.let { inDays(daysTo(it, day)) }, card, b.minPaise?.let { "min ${money(it, b.currency)}" }).joinToString(" · ")
    PassLine(
        b.title,
        sub,
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(money(b.paise, b.currency), style = Ty.mono(15, FontWeight.Bold).copy(color = p.ink))
                if (due != null) Stamp("DUE ${shortDay(due).uppercase()}", Modifier.padding(top = 8.dp, end = 4.dp))
            }
        },
        actions = {
            Btn("Mark paid", go = true, onClick = onPay)
            if (cred != null) Btn("Pay with CRED") { onCred(cred) }
        },
    )
}
