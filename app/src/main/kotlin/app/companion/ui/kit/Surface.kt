package app.companion.ui.kit

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.companion.ui.Pal
import app.companion.ui.Ty
import app.companion.ui.animationsOn
import app.companion.ui.pal

val CardShape = RoundedCornerShape(26.dp)

fun Modifier.lift(p: Pal, shape: Shape = CardShape): Modifier =
    if (p.dark) this else shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.10f))

fun Modifier.part(p: Pal, first: Boolean, last: Boolean, inset: Dp = 72.dp): Modifier {
    val r = 26.dp
    val s = RoundedCornerShape(if (first) r else 0.dp, if (first) r else 0.dp, if (last) r else 0.dp, if (last) r else 0.dp)
    val m = padding(horizontal = 16.dp).fillMaxWidth().clip(s).background(p.card)
    return if (first) m else m.drawBehind { drawRect(p.line, Offset(inset.toPx(), 0f), Size(size.width - inset.toPx() - 18.dp.toPx(), 1.dp.toPx())) }
}

@Composable
fun ToolButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = desc, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, Modifier.size(24.dp), tint = pal.ink)
    }
}

@Composable
fun Screen(
    title: String,
    sub: String,
    modifier: Modifier = Modifier,
    bar: String = title,
    back: (() -> Unit)? = null,
    tall: Boolean = true,
    nav: Boolean = true,
    state: LazyListState = rememberLazyListState(),
    tools: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val p = pal
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val head = (LocalConfiguration.current.screenHeightDp * if (tall) 0.26f else 0.2f).dp.coerceIn(150.dp, 300.dp)
    val span = with(LocalDensity.current) { (head - 56.dp).toPx() }
    val t = remember(state, span) {
        derivedStateOf { if (state.firstVisibleItemIndex > 0) 1f else (state.firstVisibleItemScrollOffset / span).coerceIn(0f, 1f) }
    }
    Box(modifier.fillMaxSize().background(p.bg)) {
        LazyColumn(Modifier.fillMaxSize(), state, PaddingValues(bottom = bottom + if (nav) 112.dp else 32.dp)) {
            item(key = "head", contentType = "head") { Head(title, sub, top, head, t) }
            content()
        }
        Row(
            Modifier.fillMaxWidth().drawBehind { drawRect(p.bg.copy(alpha = t.value)) }.padding(top = top).height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (back != null) ToolButton(Ic.Back, "Back", back)
            Text(
                bar,
                Modifier.weight(1f).padding(start = if (back == null) 20.dp else 4.dp).graphicsLayer { alpha = ((t.value - 0.55f) / 0.45f).coerceIn(0f, 1f) }
                    .clearAndSetSemantics {},
                style = Ty.ui(20, FontWeight.Bold).copy(color = p.ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            tools()
        }
    }
}

@Composable
private fun Head(title: String, sub: String, top: Dp, head: Dp, t: State<Float>) {
    val p = pal
    Column(
        Modifier.fillMaxWidth().heightIn(min = top + head).padding(start = 24.dp, end = 24.dp, top = top + 56.dp, bottom = 22.dp)
            .graphicsLayer {
                val s = 1f - 0.12f * t.value
                alpha = 1f - t.value
                scaleX = s
                scaleY = s
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f)
            },
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(title, Modifier.semantics { heading() }, style = Ty.ui(32, FontWeight.Bold).copy(color = p.ink, lineHeight = 38.sp, letterSpacing = (-0.4).sp))
        if (sub.isNotEmpty()) Text(sub, Modifier.padding(top = 10.dp), style = Ty.ui(14, FontWeight.Normal).copy(color = p.ink2, lineHeight = 20.sp))
    }
}

@Composable
fun Section(title: String, action: String? = null, onAction: () -> Unit = {}) {
    val p = pal
    Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 12.dp, top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).padding(vertical = 12.dp).semantics { heading() }, style = Ty.ui(14).copy(color = p.ink2))
        if (action != null) TextBtn(action, onClick = onAction)
    }
}

@Composable
fun Group(modifier: Modifier = Modifier, raised: Boolean = false, content: @Composable () -> Unit) {
    val p = pal
    Column(
        modifier.padding(horizontal = 16.dp).fillMaxWidth().let { if (raised) it.lift(p) else it }.clip(CardShape).background(p.card),
    ) { content() }
}

@Composable
fun Lead(icon: ImageVector, tone: Tone = Tone.Accent) {
    val p = pal
    val (bg, fg) = tone.colors(p)
    Box(Modifier.size(40.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(20.dp), tint = fg)
    }
}

enum class Tone {
    Accent, Red, Green, Plain;

    fun colors(p: Pal) = when (this) {
        Accent -> p.accentBox to p.onAccentBox
        Red -> p.redBox to p.red
        Green -> p.greenBox to p.green
        Plain -> p.raised to p.ink2
    }
}

@Composable
fun PassLine(
    title: String,
    sub: String,
    modifier: Modifier = Modifier,
    tags: List<String> = emptyList(),
    lead: ImageVector? = null,
    tone: Tone = Tone.Accent,
    lines: Int = 2,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val p = pal
    Column(modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }) {
        Row(Modifier.heightIn(min = 64.dp).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (lead != null) {
                Lead(lead, tone)
                Box(Modifier.size(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = Ty.ui(16, FontWeight.Medium).copy(color = p.ink), maxLines = lines, overflow = TextOverflow.Ellipsis)
                if (sub.isNotEmpty()) Text(sub, Modifier.padding(top = 2.dp), style = Ty.mono(13, FontWeight.Normal).copy(color = p.ink2), maxLines = lines, overflow = TextOverflow.Ellipsis)
                Tags(tags, Modifier.padding(top = 6.dp))
            }
            if (trailing != null) {
                Box(Modifier.padding(start = 12.dp)) { trailing() }
            }
        }
        if (actions != null) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(start = if (lead != null) 72.dp else 18.dp, end = 12.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

@Composable
fun Rule(start: Dp = 18.dp) {
    Box(Modifier.fillMaxWidth().padding(start = start, end = 18.dp).height(1.dp).background(pal.line))
}

internal fun LazyListScope.empty(icon: ImageVector, title: String, body: String, action: (@Composable () -> Unit)? = null) {
    item(key = "empty", contentType = "empty") { Empty(icon, title, body, action = action) }
}

@Composable
fun Empty(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val p = pal
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.rise().size(72.dp).background(p.card, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(30.dp), tint = p.ink2)
        }
        Text(title, Modifier.rise(1).padding(top = 18.dp).semantics { heading() }, style = Ty.ui(18, FontWeight.Bold).copy(color = p.ink))
        Text(body, Modifier.padding(top = 6.dp), style = Ty.ui(14, FontWeight.Normal).copy(color = p.ink2, lineHeight = 20.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
        if (action != null) Box(Modifier.padding(top = 16.dp)) { action() }
    }
}

@Composable
fun Skel(modifier: Modifier = Modifier, lines: Int = 3) {
    val p = pal
    val on = animationsOn()
    val a = if (on) {
        rememberInfiniteTransition(label = "skel").animateFloat(0.45f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
    } else {
        null
    }
    Column(modifier.padding(18.dp).graphicsLayer { alpha = a?.value ?: 0.7f }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth(0.4f).height(14.dp).background(p.raised, RoundedCornerShape(7.dp)))
        Box(Modifier.fillMaxWidth(0.6f).height(34.dp).background(p.raised, RoundedCornerShape(10.dp)))
        repeat(lines) { Box(Modifier.fillMaxWidth(if (it % 2 == 0) 0.9f else 0.7f).height(12.dp).background(p.raised, RoundedCornerShape(6.dp))) }
    }
}

@Composable
fun Quiet(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(horizontal = 28.dp, vertical = 8.dp), style = Ty.ui(14, FontWeight.Normal).copy(color = pal.ink2))
}
