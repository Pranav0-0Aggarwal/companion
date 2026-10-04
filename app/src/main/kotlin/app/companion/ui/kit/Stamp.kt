package app.companion.ui.kit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.companion.ui.Ty
import app.companion.ui.pal

enum class Ink { Red, Green, Quiet, Amber }

@Composable
fun Stamp(text: String, modifier: Modifier = Modifier, ok: Boolean = false, thump: Boolean = false, ink: Ink = if (ok) Ink.Green else Ink.Red) {
    val p = pal
    val c: Color = when (ink) {
        Ink.Red -> p.red
        Ink.Green -> p.green
        Ink.Quiet -> p.ink2
        Ink.Amber -> p.amber
    }
    val on = motion()
    val k = remember { Animatable(if (thump && on) 0f else 1f) }
    LaunchedEffect(thump) { if (thump && on) k.animateTo(1f, Motion.bouncy()) }
    Box(
        modifier.graphicsLayer {
            val v = k.value
            val s = 1.35f - 0.35f * v
            scaleX = s
            scaleY = s
            rotationZ = -8f * (1f - v)
            alpha = v.coerceIn(0f, 1f)
        }.border(1.5.dp, c, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, style = Ty.ui(11, FontWeight.ExtraBold).copy(color = c, letterSpacing = 1.2.sp), maxLines = 1)
    }
}

@Composable
fun StateStamp(text: String, ink: Ink, modifier: Modifier = Modifier) {
    AnimatedContent(
        text to ink,
        modifier,
        transitionSpec = { fadeIn(Motion.snappy()) togetherWith fadeOut(Motion.snappy()) },
        label = "stamp",
    ) { (t, i) -> Stamp(t, ink = i, thump = transition.currentState != transition.targetState) }
}
