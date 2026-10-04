package app.companion.ui.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.companion.ui.Ty
import app.companion.ui.animationsOn
import app.companion.ui.pal
import kotlinx.coroutines.delay

private class Notched(private val x: Float, private val r: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val body = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(Offset.Zero, size), androidx.compose.ui.geometry.CornerRadius(12f))) }
        val cuts = Path().apply {
            addOval(Rect(Offset(x - r, -r), Size(r * 2, r * 2)))
            addOval(Rect(Offset(x - r, size.height - r), Size(r * 2, r * 2)))
        }
        return Outline.Generic(Path.combine(PathOperation.Difference, body, cuts))
    }
}

fun Modifier.vertical(): Modifier = layout { m, c ->
    val p = m.measure(c.copy(maxWidth = Constraints.Infinity))
    layout(p.height, p.width) {
        p.placeWithLayer(-(p.width - p.height) / 2, (p.width - p.height) / 2) { rotationZ = -90f }
    }
}

@Composable
fun Coupon(
    service: String,
    code: String,
    meta: String,
    timeLeft: String,
    remaining: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val p = pal
    val notch = with(androidx.compose.ui.platform.LocalDensity.current) { Notched(18.dp.toPx(), 7.dp.toPx()) }
    val dash = with(androidx.compose.ui.platform.LocalDensity.current) { 4.dp.toPx() }
    Row(
        modifier.height(IntrinsicSize.Min).clip(notch).background(if (p.dark) p.card else Color.White)
            .clickable(role = Role.Button, onClickLabel = "Copy code", onClick = onClick)
            .drawBehind {
                val x = 18.dp.toPx()
                drawLine(p.rule, Offset(x, 10.dp.toPx()), Offset(x, size.height - 10.dp.toPx()), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)))
                drawLine(
                    p.stamp, Offset(x - 1.dp.toPx(), 10.dp.toPx()),
                    Offset(x - 1.dp.toPx(), 10.dp.toPx() + (size.height - 20.dp.toPx()) * remaining.coerceIn(0f, 1f)),
                    2.dp.toPx(), cap = StrokeCap.Round,
                )
            },
    ) {
        Box(Modifier.width(18.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text(timeLeft, Modifier.vertical(), style = Ty.mono(10, FontWeight.Bold).copy(color = p.stamp, letterSpacing = 0.06.sp), maxLines = 1)
        }
        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
            Text(service, style = Ty.ui(12).copy(color = p.ink2), maxLines = 1)
            Text(code, Modifier.padding(top = 2.dp), style = Ty.mono(26, FontWeight.ExtraBold).copy(color = p.ink, letterSpacing = 0.02.sp), maxLines = 1)
            Text(meta, Modifier.padding(top = 4.dp), style = Ty.mono(11).copy(color = p.ink2), maxLines = 1)
        }
    }
}

@Composable
fun Stamp(text: String, modifier: Modifier = Modifier, ok: Boolean = false, thump: Boolean = false, size: Int = 11) {
    val p = pal
    val c = if (ok) p.settled else p.stamp
    val on = animationsOn()
    val k = remember { Animatable(if (thump) 0f else 1f) }
    LaunchedEffect(thump) { if (thump) k.animateTo(1f, androidx.compose.animation.core.tween(if (on) 420 else 160)) }
    Box(
        modifier.graphicsLayer {
            val s = if (on) 1.9f - 0.9f * k.value else 1f
            scaleX = s
            scaleY = s
            alpha = 0.88f * k.value
            rotationZ = if (on) -14f + 8f * k.value else -6f
        }.border(1.dp, c, RoundedCornerShape(3.dp)).padding(2.dp).border(2.dp, c, RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, style = Ty.mono(size, FontWeight.ExtraBold).copy(color = c, letterSpacing = 0.12.sp), maxLines = 1)
    }
}

@Composable
fun Printed(text: String, style: androidx.compose.ui.text.TextStyle, animate: Boolean, modifier: Modifier = Modifier) {
    val on = animate && animationsOn()
    var n by remember(text) { mutableIntStateOf(if (on) 0 else text.length) }
    LaunchedEffect(text, on) {
        while (n < text.length) {
            delay(28)
            n++
        }
    }
    Text(text.take(n), modifier, style = style, maxLines = 1)
}
