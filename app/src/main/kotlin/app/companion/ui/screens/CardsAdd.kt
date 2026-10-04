package app.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.companion.core.BankNames
import app.companion.data.Card
import app.companion.ui.Ty
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Field
import app.companion.ui.kit.Ic
import app.companion.ui.kit.IconRow
import app.companion.ui.pal

private fun ord(n: Int) = "$n" + if (n in 11..13) "th" else when (n % 10) {
    1 -> "st"
    2 -> "nd"
    3 -> "rd"
    else -> "th"
}

private val days = (1..31).toList()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCard(onDismiss: () -> Unit, onAdd: (Card) -> Unit) {
    val p = pal
    var bank by remember { mutableStateOf<String?>(null) }
    var nick by remember { mutableStateOf("") }
    var last4 by remember { mutableStateOf("") }
    var stmt by remember { mutableStateOf<Int?>(null) }
    var due by remember { mutableStateOf<Int?>(null) }
    var limit by remember { mutableStateOf("") }
    val b = bank
    val s = stmt
    val d = due
    val card = if (b != null && s != null && d != null && last4.length == 4) {
        Card(bank = b, nick = nick.trim(), last4 = last4, stmtDay = s, dueDay = d, limit = limit.toLongOrNull()?.takeIf { it > 0 }?.times(100))
    } else {
        null
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = p.card, contentColor = p.ink) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add a card", style = Ty.ui(20, FontWeight.ExtraBold).copy(color = p.ink))
            Text("Only what's needed to recognise its messages. No card number, no CVV.", style = Ty.ui(13, FontWeight.Medium).copy(color = p.ink2))
            Pick("Bank", "Choose a bank", b, BankNames, { it }) { bank = it }
            Field("Card name (optional)", nick, { nick = it.take(30) }, hint = "Regalia Gold")
            Field("Last 4 digits", last4, { if (digits(it, 4)) last4 = it }, keyboard = KeyboardType.Number, mono = true, hint = "1234")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pick("Statement day", "Select", s?.let(::ord), days, ::ord, Modifier.weight(1f)) { stmt = it }
                Pick("Due day", "Select", d?.let(::ord), days, ::ord, Modifier.weight(1f)) { due = it }
            }
            Field("Credit limit in rupees (optional)", limit, { if (digits(it, 9)) limit = it }, keyboard = KeyboardType.Number, hint = "50000")
            IconRow(Ic.Shield, "Stays on this phone, encrypted")
            Btn("Add card", Modifier.fillMaxWidth().alpha(if (card != null) 1f else 0.4f), go = true) { card?.let(onAdd) }
        }
    }
}

@Composable
private fun <T> Pick(label: String, hint: String, value: String?, options: List<T>, text: (T) -> String, modifier: Modifier = Modifier, onPick: (T) -> Unit) {
    val p = pal
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.fillMaxWidth()) {
        Text(label, Modifier.padding(bottom = 4.dp), style = Ty.ui(12).copy(color = p.ink2))
        Box {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).background(p.card, shape).border(1.dp, p.rule, shape)
                    .clickable(role = Role.DropdownList) { open = true }.padding(start = 14.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(value ?: hint, Modifier.weight(1f), style = Ty.ui(15).copy(color = if (value != null) p.ink else p.ink2))
                Icon(Icons.Filled.ArrowDropDown, null, tint = p.ink2)
            }
            DropdownMenu(open, { open = false }, containerColor = p.card) {
                options.forEach { o ->
                    DropdownMenuItem(text = { Text(text(o), style = Ty.ui(15).copy(color = p.ink)) }, onClick = { onPick(o); open = false })
                }
            }
        }
    }
}
