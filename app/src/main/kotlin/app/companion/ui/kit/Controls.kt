package app.companion.ui.kit

import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.companion.ui.Ty
import app.companion.ui.pal

private val PillShape = RoundedCornerShape(50)

@Composable
fun Btn(text: String, modifier: Modifier = Modifier, go: Boolean = false, enabled: Boolean = true, icon: ImageVector? = null, dense: Boolean = false, onClick: () -> Unit) {
    val p = pal
    val src = remember { MutableInteractionSource() }
    val bg = if (go) p.accent else p.accentBox
    val fg = if (go) p.onAccent else p.onAccentBox
    Row(
        modifier.minimumInteractiveComponentSize().press(src, if (go) 0.94f else 0.97f).graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .heightIn(min = 40.dp).clip(PillShape).background(bg)
            .clickable(src, androidx.compose.material3.ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (dense) 10.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) Icon(icon, null, Modifier.padding(end = 8.dp).size(18.dp), tint = fg)
        Text(text, style = Ty.ui(14).copy(color = fg), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
fun TextBtn(text: String, modifier: Modifier = Modifier, color: Color = pal.accent, onClick: () -> Unit) {
    Box(
        modifier.minimumInteractiveComponentSize().clip(PillShape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Ty.ui(14).copy(color = color), maxLines = 1)
    }
}

@Composable
fun Chip(text: String, on: Boolean, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    val p = pal
    val bg by animateColorAsState(if (on) p.accentBox else p.card, Motion.soft(), label = "chip")
    Row(
        modifier.minimumInteractiveComponentSize().height(36.dp).clip(PillShape).background(bg)
            .let { if (on) it else it.border(1.dp, p.line, PillShape) }
            .semantics { selected = on }
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.padding(end = 6.dp).size(16.dp), tint = if (on) p.onAccentBox else p.ink2)
        Text(text, style = Ty.ui(14, if (on) FontWeight.SemiBold else FontWeight.Medium).copy(color = if (on) p.onAccentBox else p.ink), maxLines = 1)
    }
}

@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    val p = pal
    Text(
        text,
        modifier.background(p.raised, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        style = Ty.ui(12).copy(color = p.ink2),
        maxLines = 1,
    )
}

@Composable
fun Tags(tags: List<String>, modifier: Modifier = Modifier) {
    if (tags.isNotEmpty()) Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) { tags.take(4).forEach { Tag(it) } }
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    mono: Boolean = false,
    hint: String = "",
    lines: Int = 1,
    ime: ImeAction = ImeAction.Default,
    onIme: () -> Unit = {},
) {
    val p = pal
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val up = focused || value.isNotEmpty()
    val f by animateFloatAsState(if (up) 1f else 0f, Motion.snappy(), label = "label")
    val edge by animateColorAsState(if (focused) p.accent else Color.Transparent, Motion.soft(), label = "edge")
    val shape = RoundedCornerShape(16.dp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = lines == 1,
        minLines = lines,
        interactionSource = src,
        cursorBrush = SolidColor(p.accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ime),
        keyboardActions = KeyboardActions(onAny = { onIme() }),
        textStyle = (if (mono) Ty.mono(16) else Ty.ui(16, FontWeight.Normal)).copy(color = p.ink),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).background(if (focused) p.card else p.raised, shape).border(2.dp, edge, shape)
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    label,
                    Modifier.padding(top = 19.dp).graphicsLayer {
                        val s = 1f - 0.25f * f
                        scaleX = s
                        scaleY = s
                        translationY = -11.dp.toPx() * f
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
                    style = Ty.ui(16, FontWeight.Medium).copy(color = if (focused) p.accent else p.ink2),
                    maxLines = 1,
                )
                Box(Modifier.padding(top = 26.dp, bottom = 10.dp)) {
                    if (value.isEmpty() && focused && hint.isNotEmpty()) Text(hint, style = Ty.ui(16, FontWeight.Normal).copy(color = p.ink2))
                    inner()
                }
            }
        },
    )
}

@Composable
fun Toggle(label: String, sub: String, on: Boolean, modifier: Modifier = Modifier, icon: ImageVector? = null, onChange: (Boolean) -> Unit) {
    val p = pal
    Row(
        modifier.fillMaxWidth().toggleable(on, role = Role.Switch, onValueChange = onChange).heightIn(min = 64.dp).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Lead(icon, if (on) Tone.Accent else Tone.Plain)
            Box(Modifier.size(14.dp))
        }
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = Ty.ui(16, FontWeight.Medium).copy(color = p.ink))
            if (sub.isNotEmpty()) Text(sub, Modifier.padding(top = 2.dp), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2))
        }
        Switch(
            checked = on,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = p.accent, checkedThumbColor = p.onAccent, checkedBorderColor = p.accent,
                uncheckedTrackColor = p.raised, uncheckedBorderColor = p.ink3, uncheckedThumbColor = p.ink2,
            ),
        )
    }
}

@Composable
fun IconRow(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    val p = pal
    Row(modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = p.green)
        Text(text, Modifier.padding(start = 8.dp), style = Ty.ui(13, FontWeight.Medium).copy(color = p.green, fontSize = 13.sp))
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val p = pal
    val x = remember { androidx.compose.animation.core.Animatable(selected.toFloat()) }
    androidx.compose.runtime.LaunchedEffect(selected) { x.animateTo(selected.toFloat(), Motion.snappy()) }
    Row(
        modifier.fillMaxWidth().height(44.dp).clip(PillShape).background(p.raised).padding(3.dp)
            .drawBehind {
                val w = size.width / options.size
                drawRoundRect(p.accentBox, androidx.compose.ui.geometry.Offset(w * x.value, 0f), androidx.compose.ui.geometry.Size(w, size.height), androidx.compose.ui.geometry.CornerRadius(size.height / 2))
            },
    ) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(PillShape).semantics { this.selected = on }.clickable(role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(o, style = Ty.ui(14, if (on) FontWeight.SemiBold else FontWeight.Medium).copy(color = if (on) p.onAccentBox else p.ink2), maxLines = 1)
            }
        }
    }
}
