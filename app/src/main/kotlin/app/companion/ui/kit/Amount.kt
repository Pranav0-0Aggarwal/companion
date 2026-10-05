package app.companion.ui.kit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun TextMeasurer.width(text: String, style: TextStyle) = measure(text, style, softWrap = false, maxLines = 1).size.width

private fun fit(m: TextMeasurer, text: String, style: TextStyle, w: Int, floor: Float): Float {
    var s = style.fontSize.value
    val lo = s * floor
    while (s > lo && m.width(text, style.copy(fontSize = s.sp)) * 1.03f > w) s -= 0.5f
    return s
}

@Composable
fun Amount(text: String, style: TextStyle, modifier: Modifier = Modifier, roll: Boolean = true, floor: Float = 0.5f) {
    val m = rememberTextMeasurer()
    val d = LocalDensity.current
    BoxWithConstraints(modifier) {
        val w = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val s = remember(text, style, w, d) { if (w == Int.MAX_VALUE) style.fontSize.value else fit(m, text, style, w, floor) }
        val fitted = style.copy(fontSize = s.sp)
        if (roll) Roll(text, fitted) else Text(text, style = fitted, maxLines = 1, softWrap = false)
    }
}

@Composable
fun AmountPair(
    first: Pair<String, String>,
    second: Pair<String, String>?,
    firstStyle: TextStyle,
    secondStyle: TextStyle,
    labelStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val m = rememberTextMeasurer()
    val d = LocalDensity.current
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth
        val gap = with(d) { 16.dp.roundToPx() }
        val one = second == null || m.width(first.second, firstStyle) + gap + maxOf(m.width(second.second, secondStyle), m.width(second.first, labelStyle)) <= w
        if (one) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(first.first, style = labelStyle)
                    Amount(first.second, firstStyle)
                }
                if (second != null) {
                    Column(Modifier.padding(start = 16.dp), horizontalAlignment = Alignment.End) {
                        Text(second.first, style = labelStyle)
                        Amount(second.second, secondStyle)
                    }
                }
            }
        } else {
            Column {
                Text(first.first, style = labelStyle)
                Amount(first.second, firstStyle)
                if (second != null) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Start) {
                        Text(second.first, style = labelStyle)
                        Spacer(Modifier.width(8.dp))
                        Amount(second.second, secondStyle)
                    }
                }
            }
        }
    }
}
