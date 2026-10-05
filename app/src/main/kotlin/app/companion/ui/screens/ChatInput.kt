package app.companion.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.companion.ui.Ty
import app.companion.ui.kit.Ic
import app.companion.ui.kit.motion
import app.companion.ui.pal

@Composable
internal fun ChatInput(q: TextFieldValue, set: (TextFieldValue) -> Unit, send: () -> Unit, listen: (() -> Unit)?, listening: Boolean, busy: Boolean, stop: () -> Unit, hint: String, modifier: Modifier) {
    val p = pal
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp)).background(p.card).padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
            if (q.text.isEmpty()) Text(hint, style = Ty.ui(17, FontWeight.Normal).copy(color = p.ink2), maxLines = 1)
            BasicTextField(
                q, set, modifier.fillMaxWidth().semantics { contentDescription = "Message" },
                textStyle = Ty.ui(17, FontWeight.Medium).copy(color = p.ink),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                maxLines = 4,
            )
        }
        when {
            busy -> Round(Ic.Stop, "Stop", p.accentBox, p.onAccentBox, stop)
            q.text.isBlank() -> if (listen != null) Mic(listening, listen)
            else -> Round(Ic.Send, "Send", p.accent, p.onAccent, send)
        }
    }
}

@Composable
private fun Round(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(bg).clickable(role = Role.Button, onClickLabel = desc, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, Modifier.size(22.dp), tint = fg)
    }
}

@Composable
internal fun Input(q: String, set: (String) -> Unit, go: () -> Unit, listen: (() -> Unit)?, listening: Boolean, modifier: Modifier) {
    val p = pal
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(24.dp)).background(p.card)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
            if (q.isEmpty()) Text("Ask about your money", style = Ty.ui(20, FontWeight.Normal).copy(color = p.ink2))
            BasicTextField(
                q, set, modifier.fillMaxWidth().semantics { contentDescription = "Question" },
                textStyle = Ty.ui(20, FontWeight.Medium).copy(color = p.ink),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { go() }),
                maxLines = 3,
            )
        }
        if (q.isBlank()) {
            if (listen != null) Mic(listening, listen)
        } else {
            Round(Ic.Send, "Ask", p.accent, p.onAccent, go)
        }
    }
}

@Composable
internal fun Mic(listening: Boolean, onClick: () -> Unit) {
    val p = pal
    val on = motion() && listening
    val b = if (on) rememberInfiniteTransition(label = "mic").animateFloat(1f, 1.35f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "b") else null
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        if (listening) {
            Box(
                Modifier.size(44.dp).graphicsLayer {
                    val s = b?.value ?: 1.15f
                    scaleX = s
                    scaleY = s
                    alpha = 1.4f - s
                }.border(2.dp, p.accent, CircleShape),
            )
        }
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(if (listening) p.accentBox else p.raised).clickable(role = Role.Button, onClickLabel = "Ask by voice", onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Ic.Mic, if (listening) "Listening" else "Ask by voice", Modifier.size(22.dp), tint = if (listening) p.onAccentBox else p.ink2)
        }
    }
}
