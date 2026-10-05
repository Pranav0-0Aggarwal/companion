package app.companion.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.data.Item
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.clock
import app.companion.ui.dateOf
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Chip
import app.companion.ui.kit.LocalSnack
import app.companion.ui.kit.MoneyRow
import app.companion.ui.kit.Quiet
import app.companion.ui.kit.TextBtn
import app.companion.ui.kit.part
import app.companion.ui.money
import app.companion.ui.pal
import app.companion.ui.shortDay
import kotlinx.coroutines.launch

private fun Item.line() = listOfNotNull(shortDay(dateOf(at)), clock(at), bank, last4?.let { "··$it" }).joinToString(" · ")

@Composable
fun DupPanel(i: Item, modifier: Modifier = Modifier) {
    val p = pal
    val repo = LocalContext.current.sl.repo
    val scope = rememberCoroutineScope()
    val dups by remember(i.id) { repo.dups(i.id) }.collectAsStateWithLifecycle(emptyList())
    var pick by remember { mutableStateOf(false) }
    var list by remember { mutableStateOf(false) }
    Column(modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Btn("Same as…", dense = true) { pick = true }
            if (dups.isNotEmpty()) Chip(if (dups.size == 1) "1 duplicate" else "${dups.size} duplicates", list) { list = !list }
        }
        AnimatedVisibility(list && dups.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 8.dp)) {
                dups.forEach { d ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.title, style = Ty.ui(14, FontWeight.Medium).copy(color = p.ink), maxLines = 1)
                            Text("${d.line()} · ${money(d.paise, d.currency)}", style = Ty.mono(12, FontWeight.Normal).copy(color = p.ink2), maxLines = 1)
                        }
                        TextBtn("Not a duplicate") { scope.launch { repo.unDup(d.id) } }
                    }
                }
            }
        }
    }
    if (pick) DupPicker(i) { pick = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DupPicker(i: Item, onClose: () -> Unit) {
    val p = pal
    val repo = LocalContext.current.sl.repo
    val snack = LocalSnack.current
    val rows by produceState<List<Item>?>(null, i.id) { value = repo.dupCandidates(i.id) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = p.bg, contentColor = p.ink) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 20.dp)) {
            Text("Same as…", Modifier.padding(horizontal = 24.dp), style = Ty.ui(22, FontWeight.Bold).copy(color = p.ink))
            Text(
                "Pick the one this repeats. It leaves your lists and totals.",
                Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 16.dp),
                style = Ty.ui(14, FontWeight.Normal).copy(color = p.ink2),
            )
            val r = rows
            if (r != null && r.isEmpty()) Quiet("Nothing close in time to match.")
            r?.forEachIndexed { k, c ->
                MoneyRow(
                    c.title, c.line(), money(c.paise, c.currency), c.kind == "Credit",
                    Modifier.part(p, k == 0, k == r.lastIndex, 18.dp),
                    onClick = {
                        onClose()
                        snack.undoable("Marked as duplicate", { repo.markDup(i.id, c.id) }, { repo.unDup(i.id) })
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DupSheet(i: Item, onClose: () -> Unit) {
    val p = pal
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = p.bg, contentColor = p.ink) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 20.dp)) {
            Text(i.title, style = Ty.ui(22, FontWeight.Bold).copy(color = p.ink))
            Text("${money(i.paise, i.currency)} · ${i.line()}", Modifier.padding(top = 4.dp, bottom = 16.dp), style = Ty.mono(13, FontWeight.Normal).copy(color = p.ink2))
            DupPanel(i)
        }
    }
}
