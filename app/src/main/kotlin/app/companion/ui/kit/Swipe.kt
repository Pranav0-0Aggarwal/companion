package app.companion.ui.kit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.companion.ui.Ty
import app.companion.ui.pal
import kotlinx.coroutines.launch

@Composable
fun SwipeAccept(label: String, onAccept: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    val p = pal
    if (!enabled) {
        Box(modifier.fillMaxWidth().background(p.card)) { content() }
        return
    }
    val accept by rememberUpdatedState(onAccept)
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val x = remember { Animatable(0f) }
    var w by remember { mutableIntStateOf(1) }
    var raw by remember { mutableFloatStateOf(0f) }
    val th = w * 0.3f
    fun band(r: Float) = if (r <= th) r else th + (r - th) * 0.3f
    Box(
        modifier.fillMaxWidth().onSizeChanged { w = it.width }
            .semantics { customActions = listOf(CustomAccessibilityAction(label) { accept(); true }) },
    ) {
        Row(
            Modifier.matchParentSize().drawBehind { drawRect(p.greenBox) }.padding(start = 24.dp)
                .graphicsLayer { alpha = (x.value / th).coerceIn(0f, 1f) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Ic.Check, null,
                Modifier.size(22.dp).graphicsLayer {
                    val f = (x.value / th).coerceIn(0f, 1f)
                    scaleX = 0.6f + 0.4f * f
                    scaleY = 0.6f + 0.4f * f
                },
                tint = p.green,
            )
            Text(label, Modifier.padding(start = 10.dp), style = Ty.ui(14, FontWeight.SemiBold).copy(color = p.green), maxLines = 1)
        }
        Box(
            Modifier.fillMaxWidth().graphicsLayer { translationX = x.value }.background(p.card)
                .pointerInput(th) {
                    detectHorizontalDragGestures(
                        onDragStart = { raw = x.value },
                        onDragEnd = {
                            scope.launch {
                                val hit = x.value >= th
                                if (hit) accept()
                                x.animateTo(0f, Motion.bouncy())
                            }
                        },
                        onDragCancel = { scope.launch { x.animateTo(0f, Motion.bouncy()) } },
                    ) { ch, d ->
                        ch.consume()
                        val before = raw >= th
                        raw = (raw + d).coerceAtLeast(0f)
                        val after = raw >= th
                        if (before != after) haptic(if (after) HapticFeedbackType.GestureThresholdActivate else HapticFeedbackType.SegmentTick)
                        scope.launch { x.snapTo(band(raw)) }
                    }
                },
        ) { content() }
    }
}
