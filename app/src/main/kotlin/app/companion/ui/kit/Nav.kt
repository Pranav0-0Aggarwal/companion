package app.companion.ui.kit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.companion.ui.Ty
import app.companion.ui.pal

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Today("today", "Today", Ic.Today),
    Ledger("ledger", "Ledger", Ic.Ledger),
    Cards("cards", "Cards", Ic.Cards),
    Bills("bills", "Bills", Ic.Bills),
    Inbox("inbox", "Inbox", Ic.Inbox),
}

private val PillShape = RoundedCornerShape(32.dp)

@Composable
fun FloatNav(route: String?, modifier: Modifier = Modifier, onGo: (String) -> Unit, onAsk: (Rect?) -> Unit) {
    val p = pal
    val sel = Tab.entries.indexOfFirst { it.route == route }.coerceAtLeast(0)
    val slots = remember { arrayOfNulls<Rect>(Tab.entries.size) }
    val row = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val ix = remember { Animatable(sel.toFloat()) }
    LaunchedEffect(sel) { ix.animateTo(sel.toFloat(), Motion.snappy()) }
    val askAt = remember { arrayOfNulls<Rect>(1) }
    var ready by remember { mutableStateOf(false) }
    Row(
        modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(start = 12.dp, end = 12.dp, bottom = 12.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.weight(1f).height(64.dp).shadow(14.dp, PillShape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.16f))
                .clip(PillShape).background(p.bar).border(1.dp, p.line, PillShape)
                .onGloballyPositioned { row[0] = it }
                .drawBehind {
                    if (!ready) return@drawBehind
                    val i = ix.value
                    val a = slots[i.toInt().coerceIn(0, slots.size - 1)] ?: return@drawBehind
                    val b = slots[(i.toInt() + 1).coerceIn(0, slots.size - 1)] ?: a
                    val f = i - i.toInt()
                    val base = row[0]?.boundsInRoot()?.topLeft ?: return@drawBehind
                    val l = a.left + (b.left - a.left) * f - base.x
                    drawRoundRect(p.accentBox, Offset(l, a.top - base.y), a.size, CornerRadius(a.height / 2))
                }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEachIndexed { i, t ->
                val on = i == sel
                val c by animateColorAsState(if (on) p.onAccentBox else p.ink2, Motion.soft(), label = "tab")
                Column(
                    Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp))
                        .semantics { selected = on }
                        .clickable(role = Role.Tab) { onGo(t.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.size(52.dp, 30.dp).onGloballyPositioned {
                        slots[i] = it.boundsInRoot()
                        if (!ready && slots.all { s -> s != null }) ready = true
                    }, contentAlignment = Alignment.Center) {
                        Icon(t.icon, null, Modifier.size(22.dp), tint = c)
                    }
                    Text(t.label, style = Ty.ui(11, if (on) FontWeight.Bold else FontWeight.SemiBold).copy(color = if (on) p.accent else p.ink2), maxLines = 1)
                }
            }
        }
        val src = remember { MutableInteractionSource() }
        Box(
            Modifier.size(64.dp).press(src, 0.92f).shadow(12.dp, CircleShape, ambientColor = p.accent.copy(alpha = 0.3f), spotColor = p.accent.copy(alpha = 0.4f))
                .clip(CircleShape).background(p.accent).onGloballyPositioned { askAt[0] = it.boundsInRoot() }
                .clickable(src, ripple(), role = Role.Button, onClickLabel = "Ask Companion") { onAsk(askAt[0]) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Ic.Sparkle, "Ask Companion", Modifier.size(26.dp), tint = p.onAccent)
        }
    }
}
