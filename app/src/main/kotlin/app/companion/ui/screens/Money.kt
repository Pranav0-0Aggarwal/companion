package app.companion.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.ui.kit.Segmented
import app.companion.ui.kit.motion
import kotlinx.coroutines.flow.MutableStateFlow

enum class MoneySeg(val label: String, val route: String) {
    Ledger("Ledger", "ledger"), Bills("Bills", "bills"), Cards("Cards", "cards"), Trips("Trips", "trips");

    companion object {
        val flow = MutableStateFlow(Ledger)
        val routes = entries.associateBy { it.route }
    }
}

@Composable
fun MoneyScreen(go: (String) -> Unit) {
    val seg by MoneySeg.flow.collectAsStateWithLifecycle()
    val lead = remember(seg) {
        @Composable { Segmented(MoneySeg.entries.map { it.label }, seg.ordinal, Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { MoneySeg.flow.value = MoneySeg.entries[it] } }
    }
    Crossfade(seg, animationSpec = if (motion()) tween(140) else snap(), label = "money") { s ->
        when (s) {
            MoneySeg.Ledger -> LedgerScreen(go, lead)
            MoneySeg.Bills -> BillsScreen(go, lead)
            MoneySeg.Cards -> CardsScreen(go, lead)
            MoneySeg.Trips -> TripsScreen(go, lead)
        }
    }
}
