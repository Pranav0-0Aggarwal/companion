package app.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.companion.core.Category
import app.companion.core.Kind
import app.companion.core.Labels
import app.companion.core.Senders
import app.companion.core.Types
import app.companion.data.Item
import app.companion.data.Repo
import app.companion.data.Taught
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Chip
import app.companion.ui.kit.Ic
import app.companion.ui.kit.LocalSnack
import app.companion.ui.kit.Snack
import app.companion.ui.pal

internal fun Snack.teach(repo: Repo, act: suspend () -> Taught) = go {
    val t = act()
    t.note?.let(::say)
    t.also?.let { a -> offer(a, "Also filed ${a.n} similar") { repo.undo(it) } }
    t.sender?.let { (k, l) -> offer(k, "Always treating ${Senders.name(k)} as $l") { repo.deleteSender(it) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TypeSheet(i: Item, onClose: () -> Unit) {
    val p = pal
    val repo = LocalContext.current.sl.repo
    val snack = LocalSnack.current
    val kind = Kind.valueOf(i.kind)
    val now = Types.of(kind)
    val options = Labels.pick.filter { Labels.retype(it, kind, i.paise) != null || Labels.needsAmount(it, kind, i.paise) }
    var pick by remember { mutableStateOf(now) }
    var cat by remember { mutableStateOf(i.category) }
    val money = pick in Labels.money
    val changed = pick != now || (money && cat != i.category)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = p.bg, contentColor = p.ink) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 20.dp)) {
            Text("This is…", style = Ty.ui(22, FontWeight.Bold).copy(color = p.ink))
            Text(i.title, Modifier.padding(top = 4.dp, bottom = 16.dp), style = Ty.ui(14, FontWeight.Normal).copy(color = p.ink2), maxLines = 1)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { l -> Chip(l.cap(), pick == l) { pick = l } }
            }
            if (Labels.needsAmount(pick, kind, i.paise)) {
                Text("Needs an amount in the message", Modifier.padding(top = 12.dp), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2))
            }
            if (money) {
                Text("File under", Modifier.padding(top = 16.dp, bottom = 8.dp), style = Ty.ui(13).copy(color = p.ink))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.entries.filter { it != Category.Income || pick == "income" }.forEach { c ->
                        Chip(c.label.cap(), cat == c.label, icon = Ic.of(c.label)) { cat = c.label }
                    }
                }
            }
            Btn("Apply", Modifier.padding(top = 20.dp), go = true, enabled = changed) {
                onClose()
                snack.teach(repo) { repo.retype(i.id, pick, cat.takeIf { money }) }
            }
        }
    }
}
