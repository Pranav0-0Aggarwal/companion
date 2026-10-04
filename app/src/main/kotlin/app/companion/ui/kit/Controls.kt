package app.companion.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.companion.ui.Ty
import app.companion.ui.pal

@Composable
fun Btn(text: String, modifier: Modifier = Modifier, go: Boolean = false, onClick: () -> Unit) {
    val p = pal
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.height(36.dp)
            .background(if (go) p.stamp else p.card, shape)
            .border(1.dp, if (go) p.stamp else p.rule, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Ty.ui(13).copy(color = if (go) androidx.compose.ui.graphics.Color.White else p.accent), maxLines = 1)
    }
}

@Composable
fun Chip(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = pal
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier.height(32.dp)
            .background(if (on) p.chip else androidx.compose.ui.graphics.Color.Transparent, shape)
            .border(1.dp, if (on) p.chip else p.rule, shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Ty.ui(12).copy(color = if (on) p.ink else p.ink2, fontSize = 12.5.sp), maxLines = 1)
    }
}

@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    val p = pal
    Text(
        text.uppercase(),
        modifier.border(1.dp, p.rule, RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
        style = Ty.mono(9, FontWeight.Bold).copy(color = p.ink2, letterSpacing = 0.8.sp),
        maxLines = 1,
    )
}

@Composable
fun Tags(tags: List<String>, modifier: Modifier = Modifier) {
    if (tags.isNotEmpty()) Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { tags.take(4).forEach { Tag(it) } }
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
) {
    val p = pal
    Column(modifier.fillMaxWidth()) {
        Text(label, Modifier.padding(bottom = 4.dp), style = Ty.ui(12).copy(color = p.ink2))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            textStyle = if (mono) Ty.mono(15, FontWeight.SemiBold).copy(letterSpacing = 0.2.sp, color = p.ink) else Ty.ui(15).copy(color = p.ink),
            placeholder = { Text(hint, style = Ty.ui(15, FontWeight.Normal).copy(color = p.ink2)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = p.accent, unfocusedBorderColor = p.rule, focusedContainerColor = p.card, unfocusedContainerColor = p.card,
            ),
        )
    }
}

@Composable
fun Toggle(label: String, sub: String, on: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    val p = pal
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = Ty.ui(14).copy(color = p.ink))
            if (sub.isNotEmpty()) Text(sub, Modifier.padding(top = 2.dp), style = Ty.ui(12, FontWeight.Normal).copy(color = p.ink2))
        }
        Switch(
            checked = on,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.card, uncheckedTrackColor = p.ruleSoft, uncheckedBorderColor = p.rule, uncheckedThumbColor = p.ink2),
        )
    }
}

@Composable
fun IconRow(icon: ImageVector, text: String) {
    val p = pal
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = p.settled)
        Text(text, Modifier.padding(start = 8.dp), style = Ty.ui(12, FontWeight.Medium).copy(color = p.settled))
    }
}
