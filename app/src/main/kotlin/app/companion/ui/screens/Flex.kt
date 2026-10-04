package app.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.companion.data.Item
import app.companion.data.dueDate
import app.companion.sl
import app.companion.ui.LocalFold
import app.companion.ui.Secure
import app.companion.ui.Ty
import app.companion.ui.Voice
import app.companion.ui.dayLabel
import app.companion.ui.daysTo
import app.companion.ui.inDays
import app.companion.ui.kit.Ic
import app.companion.ui.kit.Section
import app.companion.ui.kit.ToolButton
import app.companion.ui.kit.cloth
import app.companion.ui.pal
import kotlinx.coroutines.launch
import app.companion.ui.money as amt

@Composable
fun FlexScreen(go: (String) -> Unit) {
    Secure()
    val p = pal
    val repo = LocalContext.current.sl.repo
    val scope = rememberCoroutineScope()
    val d = rememberDay()
    val density = LocalDensity.current
    val fold = LocalFold.current
    BoxWithConstraints(Modifier.fillMaxSize().background(p.cover)) {
        val top = fold?.bounds?.top?.let { with(density) { it.toDp() } } ?: (maxHeight / 2)
        Column {
            Column(Modifier.fillMaxWidth().height(top).background(p.page).verticalScroll(rememberScrollState())) {
                Header(d, go)
                Otps(d.otps)
                Section("Printed today")
                TodayLines(d.printed, d.links)
            }
            Hinge(d)
            Deck(d, go, Modifier.weight(1f)) { i, c -> scope.launch { repo.file(i.id, c) } }
        }
    }
}

@Composable
private fun Header(d: Day, go: (String) -> Unit) {
    val p = pal
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Row(
        Modifier.fillMaxWidth().cloth(p).padding(start = 22.dp, end = 10.dp, top = top + 6.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Today", style = Ty.ui(28, FontWeight.ExtraBold).copy(color = p.foil))
            Text(d.sub, Modifier.padding(top = 2.dp), style = Ty.ui(13, FontWeight.Medium).copy(color = p.coverMute))
        }
        ToolButton(Ic.Search, "Search") { go("search") }
        ToolButton(Ic.More, "Settings") { go("settings") }
    }
}

@Composable
private fun Hinge(d: Day) {
    val p = pal
    val page = d.today.dayOfYear * 2
    Box(
        Modifier.fillMaxWidth().height(22.dp).background(Brush.verticalGradient(listOf(p.cover, p.cover2, p.cover))),
        contentAlignment = Alignment.Center,
    ) {
        Text("${page - 1} · $page", style = Ty.mono(10, FontWeight.SemiBold).copy(color = p.coverMute, letterSpacing = 2.sp))
    }
}

@Composable
private fun Deck(d: Day, go: (String) -> Unit, modifier: Modifier, file: (Item, String) -> Unit) {
    val p = pal
    Column(
        modifier.fillMaxWidth().background(p.cover).verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val a = d.asks.firstOrNull()
        if (a == null) {
            Text(Voice.addr(d.name, "nothing to file"), style = Ty.ui(14, FontWeight.Medium).copy(color = p.coverMute))
        } else {
            Column(Modifier.fillMaxWidth().background(p.card, RoundedCornerShape(10.dp)).padding(14.dp)) {
                Text(a.head(), style = Ty.ui(14).copy(color = p.ink))
                Text(a.srcLine(d.links), Modifier.padding(top = 2.dp), style = Ty.mono(12).copy(color = p.ink2))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    a.picks().forEachIndexed { k, c -> Pick(c.cap(), k == 0, Modifier.weight(1f)) { file(a, c) } }
                }
            }
        }
        d.bills.take(2).forEach { b ->
            Row(
                Modifier.fillMaxWidth().background(p.cover2, RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { go("bills") }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(b.title, style = Ty.ui(14, FontWeight.Bold).copy(color = p.coverInk))
                    val due = b.dueDate?.let { "${dayLabel(it)} · ${inDays(daysTo(it, d.today))}" } ?: "no due date"
                    Text(due, Modifier.padding(top = 2.dp), style = Ty.mono(12).copy(color = p.coverMute))
                }
                if (b.paise > 0) Text(amt(b.paise, b.currency), style = Ty.mono(15, FontWeight.Bold).copy(color = p.coverInk))
            }
        }
    }
}

@Composable
private fun Pick(text: String, best: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = pal
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier.heightIn(min = 44.dp).background(if (best) p.accent else p.card, shape)
            .border(1.dp, if (best) p.accent else p.rule, shape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Ty.ui(13).copy(color = if (best) MaterialTheme.colorScheme.onPrimary else p.accent),
            maxLines = 1,
        )
    }
}
