package app.companion.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.companion.core.BankNames
import app.companion.core.Found
import app.companion.data.Card
import app.companion.ui.Ty
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Field
import app.companion.ui.kit.Ic
import app.companion.ui.kit.IconRow
import app.companion.ui.pal

internal fun ord(n: Int) = "$n" + if (n in 11..13) "th" else when (n % 10) {
    1 -> "st"
    2 -> "nd"
    3 -> "rd"
    else -> "th"
}

private val days = (1..31).toList()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCard(onDismiss: () -> Unit, from: Found? = null, onAdd: (Card) -> Unit) {
    val p = pal
    var bank by remember { mutableStateOf(from?.bank) }
    var nick by remember { mutableStateOf("") }
    var last4 by remember { mutableStateOf(from?.last4.orEmpty()) }
    var stmt by remember { mutableStateOf(from?.stmtDay) }
    var due by remember { mutableStateOf(from?.dueDay) }
    var limit by remember { mutableStateOf(from?.limit?.takeIf { it >= 100 }?.div(100)?.toString().orEmpty()) }
    val b = bank
    val s = stmt
    val d = due
    val card = if (b != null && s != null && d != null && last4.length == 4) {
        Card(bank = b, nick = nick.trim(), last4 = last4, stmtDay = s, dueDay = d, limit = limit.toLongOrNull()?.takeIf { it > 0 }?.times(100))
    } else {
        null
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = p.bg, contentColor = p.ink) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add a card", style = Ty.ui(24, FontWeight.Bold).copy(color = p.ink))
            Text("Only what's needed to recognise its messages. No card number, no CVV.", style = Ty.ui(14, FontWeight.Normal).copy(color = p.ink2))
            Pick("Bank", b, BankNames, { it }) { bank = it }
            Field("Card name (optional)", nick, { nick = it.take(30) }, hint = "Regalia Gold")
            Field("Last 4 digits", last4, { if (digits(it, 4)) last4 = it }, keyboard = KeyboardType.Number, mono = true, hint = "1234")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pick("Statement day", s?.let(::ord), days, ::ord, Modifier.weight(1f)) { stmt = it }
                Pick("Due day", d?.let(::ord), days, ::ord, Modifier.weight(1f)) { due = it }
            }
            Field("Credit limit in rupees (optional)", limit, { if (digits(it, 9)) limit = it }, keyboard = KeyboardType.Number, mono = true, hint = "50000")
            IconRow(Ic.Shield, "Stays on this phone, encrypted")
            Btn("Add card", Modifier.fillMaxWidth().padding(top = 4.dp), go = true, enabled = card != null) { card?.let(onAdd) }
        }
    }
}

@Composable
private fun <T> Pick(label: String, value: String?, options: List<T>, text: (T) -> String, modifier: Modifier = Modifier, onPick: (T) -> Unit) {
    val p = pal
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(shape).background(p.raised)
                .clickable(role = Role.DropdownList, onClickLabel = "Choose $label") { open = true }.padding(start = 16.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (value != null) {
                    Text(label, style = Ty.ui(12, FontWeight.Medium).copy(color = p.ink2))
                    Text(value, style = Ty.ui(16, FontWeight.Normal).copy(color = p.ink))
                } else {
                    Text(label, style = Ty.ui(16, FontWeight.Medium).copy(color = p.ink2))
                }
            }
            Icon(Ic.Down, null, Modifier.size(20.dp), tint = p.ink2)
        }
        DropdownMenu(open, { open = false }, containerColor = p.card, shape = RoundedCornerShape(18.dp)) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(text(o), style = Ty.ui(16, FontWeight.Normal).copy(color = p.ink)) }, onClick = { onPick(o); open = false })
            }
        }
    }
}
