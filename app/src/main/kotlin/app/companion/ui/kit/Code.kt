package app.companion.ui.kit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.companion.data.Item
import app.companion.ui.Secure
import app.companion.ui.Ty
import app.companion.ui.clock
import app.companion.ui.codeText
import app.companion.ui.copy
import app.companion.ui.left
import app.companion.ui.pal
import app.companion.ui.rememberNow
import kotlinx.coroutines.delay

private fun via(src: String) = when (src) {
    "Sms" -> "SMS"
    "Notif" -> "notification"
    "Mail" -> "mail"
    "Wa" -> "WhatsApp"
    else -> src
}

@Composable
fun CodeCard(o: Item, onNotOtp: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier, big: Boolean = false) {
    Secure()
    val p = pal
    val ctx = LocalContext.current
    val haptic = rememberHaptic()
    val code = o.code.orEmpty()
    var copied by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }
    Column(
        modifier.fillMaxWidth().lift(p).clip(CardShape).background(p.card)
            .clickable(role = Role.Button, onClickLabel = "Copy code") {
                copy(ctx, code)
                haptic(HapticFeedbackType.Confirm)
                copied = true
            },
    ) {
        Row(Modifier.padding(start = 18.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(top = 6.dp)) {
                Text(o.title, style = Ty.ui(15).copy(color = p.ink), maxLines = 1)
                Text(o.note.ifBlank { "One-time code" }.replaceFirstChar(Char::uppercase), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2), maxLines = 1)
            }
            Box(Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(onClickLabel = "Dismiss code", onClick = onDismiss), contentAlignment = Alignment.Center) {
                Icon(Ic.Close, "Dismiss code", Modifier.size(18.dp), tint = p.ink2)
            }
            val r by animateFloatAsState(if (open) 180f else 0f, Motion.soft(), label = "chev")
            Box(
                Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(onClickLabel = if (open) "Hide details" else "Show details") { open = !open },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Ic.Down, if (open) "Hide details" else "Show details", Modifier.size(20.dp).graphicsLayer { rotationZ = r }, tint = p.ink2)
            }
        }
        Text(
            codeText(code),
            Modifier.padding(start = 18.dp, end = 18.dp, top = 4.dp),
            style = Ty.mono(if (big) 52 else 36, FontWeight.Bold).copy(color = p.ink, letterSpacing = 2.sp),
            maxLines = 1,
        )
        Countdown(o, copied)
        AnimatedVisibility(open, enter = expandVertically(Motion.soft()) + fadeIn(), exit = shrinkVertically(Motion.soft()) + fadeOut()) {
            Column {
                Rule()
                Row(Modifier.padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Received ${clock(o.at)} by ${via(o.src)}", Modifier.weight(1f), style = Ty.mono(13, FontWeight.Normal).copy(color = p.ink2))
                    TextBtn("Not a code", onClick = onNotOtp)
                }
            }
        }
    }
}

@Composable
private fun Countdown(o: Item, copied: Boolean) {
    val p = pal
    val now by rememberNow()
    val end = o.expires ?: 0L
    val total = (end - o.at).coerceAtLeast(1L)
    val ms = (end - now).coerceAtLeast(0L)
    val late = ms <= 15_000
    val frac by animateFloatAsState(ms.toFloat() / total, tween(1000, easing = LinearEasing), label = "drain")
    val tint by animateColorAsState(if (late) p.amber else p.accent, Motion.soft(), label = "tint")
    val on = motion()
    val pulse = if (late && on) {
        rememberInfiniteTransition(label = "pulse").animateFloat(1f, 0.55f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "p")
    } else {
        null
    }
    Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${left(ms)} left",
            Modifier.weight(1f).graphicsLayer { alpha = pulse?.value ?: 1f },
            style = Ty.mono(14).copy(color = if (late) p.amber else p.ink2),
        )
        AnimatedContent(
            copied,
            transitionSpec = { (fadeIn(Motion.snappy()) + scaleIn(Motion.bouncy(), 0.6f)) togetherWith fadeOut(Motion.snappy()) },
            label = "copy",
        ) { done ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (done) Ic.Check else Ic.Copy, null, Modifier.size(18.dp), tint = if (done) p.green else p.accent)
                Text(if (done) "Copied" else "Copy", Modifier.padding(start = 6.dp), style = Ty.ui(14).copy(color = if (done) p.green else p.accent))
            }
        }
    }
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(3.dp).drawBehind {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(p.line, cornerRadius = r)
            drawRoundRect(tint.copy(alpha = pulse?.value ?: 1f), Offset.Zero, Size(size.width * frac.coerceIn(0f, 1f), size.height), r)
        },
    )
    Box(Modifier.height(10.dp))
}
