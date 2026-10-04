package app.companion.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.data.Item
import app.companion.data.credit
import app.companion.data.money
import app.companion.sl
import app.companion.ui.Secure
import app.companion.ui.Ty
import app.companion.ui.dateOf
import app.companion.ui.kit.Field
import app.companion.ui.kit.Ic
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Rule
import app.companion.ui.kit.Screen
import app.companion.ui.kit.ToolButton
import app.companion.ui.pal
import app.companion.ui.shortDay
import kotlinx.coroutines.delay
import app.companion.ui.money as amt

private fun Item.sub() = listOfNotNull(note.take(80).takeIf { it.isNotBlank() && !money }).plus(meta()).plus(shortDay(dateOf(at))).joinToString(" · ")

@Composable
fun SearchScreen(back: () -> Unit) {
    Secure()
    val p = pal
    val repo = LocalContext.current.sl.repo
    var q by rememberSaveable { mutableStateOf("") }
    var live by remember { mutableStateOf(q) }
    LaunchedEffect(q) {
        delay(250)
        live = q
    }
    val hits by remember(live) { repo.search(live) }.collectAsStateWithLifecycle(emptyList())
    Screen(
        "Search",
        if (live.isBlank()) "On this phone only" else "${hits.size} found",
        tools = { ToolButton(Ic.Back, "Back", back) },
    ) {
        item { Field("Search", q, { q = it }, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), hint = "Merchant, bank or note") }
        if (hits.isEmpty()) {
            item { Quiet(if (live.isBlank()) "Type a merchant, bank or note" else "Nothing found") }
        }
        items(hits, key = { it.id }) { i ->
            Column {
                PassLine(
                    i.title,
                    i.sub(),
                    trailing = {
                        if (i.paise > 0) {
                            Text(amt(i.paise, i.currency), style = Ty.mono(14, FontWeight.Bold).copy(color = if (i.credit) p.settled else p.ink))
                        }
                    },
                )
                Rule()
            }
        }
    }
}
