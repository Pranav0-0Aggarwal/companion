package app.companion.ui.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.companion.ui.Ty
import app.companion.ui.pal

@Composable
fun MoneyRow(
    title: String,
    sub: String,
    amount: String,
    credit: Boolean,
    modifier: Modifier = Modifier,
    lead: ImageVector? = null,
    brand: String? = null,
    time: String? = null,
    stamp: String? = null,
    tags: List<String> = emptyList(),
    moved: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val p = pal
    Row(
        modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.heightIn(min = 64.dp).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (brand != null) {
            BrandMark(brand)
            Box(Modifier.size(14.dp))
        } else if (lead != null) {
            Lead(lead, if (moved) Tone.Plain else if (credit) Tone.Green else Tone.Accent)
            Box(Modifier.size(14.dp))
        } else if (time != null) {
            Text(time, Modifier.width(52.dp), style = Ty.mono(13, FontWeight.Normal).copy(color = p.ink2))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Ty.ui(16, FontWeight.Medium).copy(color = p.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) Text(sub, Modifier.padding(top = 2.dp), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (stamp != null || tags.isNotEmpty()) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    stamp?.let { Stamp(it, ink = Ink.Quiet) }
                    Tags(tags)
                }
            }
        }
        Text(
            if (credit && !moved) "+$amount" else amount,
            Modifier.padding(start = 12.dp),
            style = Ty.mono(16, FontWeight.SemiBold).copy(color = if (moved) p.ink2 else if (credit) p.green else p.ink),
            maxLines = 1,
        )
    }
}

@Composable
fun Pace(cur: List<Long>, typ: List<Long>, days: Int, label: String, modifier: Modifier = Modifier) {
    val p = pal
    val on = motion()
    val k = remember { Animatable(if (on) 0f else 1f) }
    LaunchedEffect(Unit) { k.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    val n = maxOf(cur.size, minOf(7, days)).coerceAtLeast(2)
    val base = typ.take(n)
    val top = maxOf(base.maxOrNull() ?: 0L, cur.maxOrNull() ?: 0L, 1L) * 1.12f
    Box(
        modifier.fillMaxWidth().height(88.dp).semantics { contentDescription = label }.drawWithCache {
            val w = size.width - 6.dp.toPx()
            val h = size.height - 8.dp.toPx()
            fun pt(i: Int, v: Long) = Offset(3.dp.toPx() + w * i / (n - 1), 4.dp.toPx() + h * (1f - v / top))
            fun line(l: List<Long>) = Path().apply { l.forEachIndexed { i, v -> pt(i, v).let { o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } } }
            val t = line(base)
            val c = line(cur)
            val m = PathMeasure().apply { setPath(c, false) }
            val seg = Path()
            val thin = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
            val bold = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
            val end = cur.lastIndex.takeIf { it >= 0 }?.let { pt(it, cur[it]) }
            val mark = cur.lastIndex.takeIf { it in base.indices }?.let { pt(it, base[it]) }
            onDrawBehind {
                drawPath(t, p.ink2, style = thin)
                mark?.let { drawCircle(p.ink2, 3.dp.toPx(), it) }
                seg.reset()
                m.getSegment(0f, m.length * k.value, seg, true)
                drawPath(seg, p.accent, style = bold)
                if (end != null && k.value > 0.98f) drawCircle(p.accent, 5.dp.toPx(), end)
            }
        },
    )
}
