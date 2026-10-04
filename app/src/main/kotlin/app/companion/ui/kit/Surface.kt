package app.companion.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import app.companion.ui.Pal
import app.companion.ui.Ty
import app.companion.ui.pal

fun Modifier.cloth(p: Pal): Modifier = drawBehind {
    drawRect(p.cover)
    val step = 3.dp.toPx()
    val light = Color.White.copy(alpha = 0.035f)
    val dark = Color.Black.copy(alpha = 0.08f)
    var y = 0f
    while (y < size.height) {
        drawLine(light, Offset(0f, y), Offset(size.width, y), 1f)
        y += step
    }
    var x = 0f
    while (x < size.width) {
        drawLine(dark, Offset(x, 0f), Offset(x, size.height), 1f)
        x += step
    }
}

@Stable
class Collapse(private val max: Float, private val min: Float) {
    var h by mutableFloatStateOf(max)
    val t get() = ((max - h) / (max - min)).coerceIn(0f, 1f)

    val conn = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y >= 0f || h <= min) return Offset.Zero
            val d = maxOf(available.y, min - h)
            h += d
            return Offset(0f, d)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y <= 0f || h >= max) return Offset.Zero
            val d = minOf(available.y, max - h)
            h += d
            return Offset(0f, d)
        }
    }
}

@Composable
fun ToolButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    val p = pal
    Box(
        Modifier.size(44.dp).background(Color.White.copy(alpha = 0.14f), CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, Modifier.size(22.dp), tint = p.coverInk)
    }
}

@Composable
fun Screen(
    title: String,
    sub: String,
    modifier: Modifier = Modifier,
    tools: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val p = pal
    val d = LocalDensity.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val maxH = with(d) { (top + 196.dp).toPx() }
    val minH = with(d) { (top + 64.dp).toPx() }
    val c = remember(maxH, minH) { Collapse(maxH, minH) }
    Column(modifier.fillMaxSize().background(p.page).nestedScroll(c.conn)) {
        Box(Modifier.fillMaxWidth().height(with(d) { c.h.toDp() }).cloth(p)) {
            Row(
                Modifier.align(Alignment.TopEnd).padding(top = top + lerp(6.dp, 10.dp, c.t), end = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                content = tools,
            )
            Column(Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 18.dp, end = 22.dp)) {
                Text(
                    title,
                    style = Ty.ui(0, FontWeight.ExtraBold).copy(
                        fontSize = (40f - 16f * c.t).sp,
                        color = p.foil,
                        letterSpacing = (-0.02).sp,
                        shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, -1f), 0f),
                    ),
                )
                if (c.t < 0.6f) {
                    Text(
                        sub,
                        Modifier.padding(top = 6.dp).graphicsLayer { alpha = 1f - c.t * 1.6f },
                        style = Ty.ui(14, FontWeight.Medium).copy(color = p.coverMute),
                    )
                }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 112.dp), content = content)
    }
}

@Composable
fun Section(title: String, action: String? = null, onAction: () -> Unit = {}) {
    val p = pal
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, Modifier.weight(1f), style = Ty.ui(15, FontWeight.Bold).copy(color = p.ink))
        if (action != null) {
            Text(action, Modifier.clickable(role = Role.Button, onClick = onAction), style = Ty.ui(13).copy(color = p.accent))
        }
    }
}

@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = pal
    Column(
        modifier.padding(horizontal = 12.dp).fillMaxWidth()
            .background(p.card, RoundedCornerShape(22.dp)),
    ) { content() }
}

@Composable
fun PassLine(
    title: String,
    sub: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val p = pal
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = Ty.ui(14).copy(color = p.ink))
                Text(sub, Modifier.padding(top = 2.dp), style = Ty.mono(12).copy(color = p.ink2))
            }
            if (trailing != null) trailing()
        }
        if (actions != null) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

@Composable
fun Rule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(pal.ruleSoft))
}
