package app.companion.ui.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.companion.core.Strip
import app.companion.ui.Ty
import app.companion.ui.dayLabel
import app.companion.ui.pal
import app.companion.ui.weekday
import java.time.LocalDate

@Composable
fun DayStrip(selected: LocalDate, today: LocalDate, modifier: Modifier = Modifier, onPick: (LocalDate) -> Unit) {
    val p = pal
    var acc by remember { mutableFloatStateOf(0f) }
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp).pointerInput(selected) {
            val step = 36.dp.toPx()
            detectHorizontalDragGestures(onDragEnd = { acc = 0f }, onDragCancel = { acc = 0f }) { _, dx ->
                acc += dx
                if (acc > step) {
                    acc = 0f
                    onPick(selected.minusDays(1))
                } else if (acc < -step) {
                    acc = 0f
                    onPick(selected.plusDays(1))
                }
            }
        },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Strip.days(selected).forEach { d ->
            val on = d == selected
            val bg by animateColorAsState(if (on) p.accentBox else androidx.compose.ui.graphics.Color.Transparent, Motion.soft(), label = "day")
            Column(
                Modifier.weight(1f).padding(horizontal = 2.dp).clip(RoundedCornerShape(18.dp)).background(bg)
                    .semantics { this.selected = on; contentDescription = dayLabel(d) }
                    .clickable(role = Role.Tab) { onPick(d) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(weekday(d), style = Ty.ui(12, FontWeight.Medium).copy(color = if (on) p.onAccentBox else p.ink2), maxLines = 1)
                Text(
                    d.dayOfMonth.toString(),
                    Modifier.padding(top = 4.dp),
                    style = Ty.mono(17, if (on || d == today) FontWeight.Bold else FontWeight.Medium).copy(color = if (on) p.onAccentBox else if (d == today) p.accent else p.ink),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun Ring(frac: Float, size: Dp, stroke: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    val p = pal
    val f by animateFloatAsState(frac.coerceIn(0f, 1f), Motion.soft(), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val s = Size(this.size.width - w, this.size.height - w)
            drawArc(p.line, 0f, 360f, false, Offset(w / 2, w / 2), s, style = Stroke(w))
            if (f > 0f) drawArc(p.accent, -90f, 360f * f, false, Offset(w / 2, w / 2), s, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
fun Spark(points: List<Double>, modifier: Modifier = Modifier, desc: String = "") {
    val p = pal
    Canvas(modifier.semantics { contentDescription = desc }) {
        if (points.size < 2) return@Canvas
        val lo = points.min()
        val hi = points.max()
        val span = (hi - lo).coerceAtLeast(0.5)
        val pad = 4.dp.toPx()
        fun pt(i: Int) = Offset(pad + (size.width - 2 * pad) * i / (points.size - 1), pad + (size.height - 2 * pad) * (1f - ((points[i] - lo) / span).toFloat()))
        val path = androidx.compose.ui.graphics.Path().apply { points.indices.forEach { i -> pt(i).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } } }
        drawPath(path, p.accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawCircle(p.accent, 4.dp.toPx(), pt(points.lastIndex))
    }
}
