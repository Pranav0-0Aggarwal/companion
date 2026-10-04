package app.companion.ui.kit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.delay

val LocalMotion = staticCompositionLocalOf { true }

object Motion {
    fun <T> soft(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
    fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
}

@Composable
fun motion(): Boolean = LocalMotion.current

@Composable
fun rememberHaptic(): (HapticFeedbackType) -> Unit {
    val h = LocalHapticFeedback.current
    return remember(h) { { t: HapticFeedbackType -> h.performHapticFeedback(t) } }
}

@Composable
fun Modifier.press(source: MutableInteractionSource, depth: Float = 0.96f): Modifier {
    val down by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (down && motion()) depth else 1f, Motion.bouncy(), label = "press")
    return graphicsLayer {
        scaleX = s
        scaleY = s
    }
}

@Composable
fun Modifier.rise(i: Int = 0): Modifier {
    val on = motion()
    val k = remember { Animatable(if (on) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (on) {
            delay(i.coerceAtMost(6) * 45L)
            k.animateTo(1f, Motion.soft())
        }
    }
    return graphicsLayer {
        alpha = k.value
        translationY = (1f - k.value) * 20.dp.toPx()
    }
}

@Composable
fun Roll(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    if (!motion()) {
        Text(text, modifier, style = style, maxLines = 1)
        return
    }
    var shown by remember { mutableStateOf(text.map { if (it.isDigit()) '0' else it }.joinToString("")) }
    LaunchedEffect(text) { shown = text }
    val n = shown.length
    Row(modifier.semantics(mergeDescendants = true) {}.clearAndSetSemantics { contentDescription = text }) {
        shown.forEachIndexed { i, ch ->
            androidx.compose.runtime.key(n - i) {
                AnimatedContent(
                    ch,
                    transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically(Motion.snappy()) { if (up) it else -it } + fadeIn(Motion.snappy())) togetherWith
                            (slideOutVertically(Motion.snappy()) { if (up) -it else it } + fadeOut(Motion.snappy()))
                    },
                    label = "roll",
                ) { c -> Text(c.toString(), style = style) }
            }
        }
    }
}
