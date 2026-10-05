package app.companion.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.companion.ui.Ty
import app.companion.ui.kit.Ic
import app.companion.ui.kit.lift
import app.companion.ui.pal
import kotlinx.coroutines.launch

class AskReq(val origin: Rect?, val text: String = "", val voice: Boolean = false)

val LocalAsk = staticCompositionLocalOf<(AskReq) -> Unit> { {} }

private val Pill = RoundedCornerShape(50)

@Composable
fun AskPill(modifier: Modifier = Modifier, hint: String = "Ask Companion") {
    val p = pal
    val ask = LocalAsk.current
    val at = remember { arrayOfNulls<Rect>(1) }
    Row(
        modifier.padding(horizontal = 16.dp).fillMaxWidth().height(56.dp).onGloballyPositioned { at[0] = it.boundsInRoot() }
            .lift(p, Pill).clip(Pill).background(p.card)
            .clickable(role = Role.Button, onClickLabel = "Ask Companion") { ask(AskReq(at[0])) }
            .padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ic.Sparkle, null, Modifier.size(22.dp), tint = p.accent)
        Text(hint, Modifier.weight(1f).padding(start = 12.dp), style = Ty.ui(16, FontWeight.Normal).copy(color = p.ink2), maxLines = 1)
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Ask by voice") { ask(AskReq(at[0], voice = true)) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Ic.Mic, "Ask by voice", Modifier.size(22.dp), tint = p.ink2)
        }
    }
}

private class Morph(private val r: Rect, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Rounded(RoundRect(r, CornerRadius(radius)))
}

private fun mix(a: Float, b: Float, f: Float) = a + (b - a) * f

@Composable
fun AskHost(req: AskReq?, onClose: () -> Unit, go: (String) -> Unit) {
    val k = remember { Animatable(0f) }
    val drag = remember { Animatable(0f) }
    var held by remember { mutableStateOf<AskReq?>(null) }
    LaunchedEffect(req) {
        if (req != null) {
            held = req
            drag.snapTo(0f)
            k.animateTo(1f, spring(0.86f, 420f))
        } else {
            launch { drag.animateTo(0f, spring(1f, 600f)) }
            if (held != null) k.animateTo(0f, spring(1f, 700f)) else k.snapTo(0f)
            held = null
        }
    }
    val r = held ?: return
    BackHandler(req != null) { onClose() }
    val p = pal
    val d = LocalDensity.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val t = with(d) { top.toPx() }
        val big = with(d) { 28.dp.toPx() }
        val o = r.origin ?: Rect(0f, h, w, h + big * 2)
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = (k.value * 0.45f).coerceIn(0f, 0.45f) }.background(Color.Black)
                .pointerInput(Unit) { detectTapGestures { onClose() } },
        )
        Box(
            Modifier.fillMaxSize().padding(top = top).graphicsLayer {
                val f = k.value
                translationY = drag.value
                clip = true
                shape = Morph(
                    Rect(mix(o.left, 0f, f), mix(o.top - t, 0f, f), mix(o.right, w, f), mix(o.bottom - t, h - t + big, f)),
                    mix(o.height / 2, big, f),
                )
            }.drawBehind { drawRect(lerp(p.card, p.bg, k.value.coerceIn(0f, 1f))) },
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = ((k.value - 0.3f) / 0.7f).coerceIn(0f, 1f) }) {
                AskSheet(r, req != null, onClose, { go(it) }, drag)
            }
        }
    }
}
