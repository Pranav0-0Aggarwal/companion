package app.companion.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.companion.ui.Ty
import app.companion.ui.pal

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Today("today", "Today", Ic.Today),
    Ledger("ledger", "Ledger", Ic.Ledger),
    Cards("cards", "Cards", Ic.Cards),
    Bills("bills", "Bills", Ic.Bills),
    Inbox("inbox", "Inbox", Ic.Inbox),
}

@Composable
fun FloatNav(route: String?, modifier: Modifier = Modifier, onGo: (String) -> Unit) {
    val p = pal
    val shape = RoundedCornerShape(32.dp)
    Row(
        modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 22.dp, vertical = 16.dp).fillMaxWidth().height(64.dp)
            .background(p.card.copy(alpha = if (p.dark) 0.78f else 0.72f), shape)
            .border(1.dp, Color.White.copy(alpha = if (p.dark) 0.08f else 0.6f), shape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { t ->
            val on = route == t.route
            Column(
                Modifier.weight(1f).clickable(role = Role.Tab, onClick = { onGo(t.route) }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.width(48.dp).height(28.dp).background(if (on) p.chip else Color.Transparent, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(t.icon, t.label, Modifier.size(20.dp), tint = if (on) p.accent else p.ink2)
                }
                Text(t.label, style = Ty.ui(10).copy(fontSize = 10.5.sp, color = if (on) p.accent else p.ink2), maxLines = 1)
            }
        }
    }
}
