package app.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.companion.chat.LogWeight
import app.companion.core.ToolOut
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Field
import app.companion.ui.kit.LocalSnack
import app.companion.ui.kit.TextBtn
import app.companion.ui.pal
import app.companion.ui.shortDay
import app.companion.ui.weekday
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = pal
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = p.bg, contentColor = p.ink) {
        Column(Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 20.dp), content = content)
    }
}

@Composable
fun SheetTitle(title: String, sub: String? = null) {
    val p = pal
    Text(title, style = Ty.ui(22, FontWeight.Bold).copy(color = p.ink))
    if (sub != null) Text(sub, Modifier.padding(top = 4.dp), style = Ty.ui(14, FontWeight.Normal).copy(color = p.ink2))
}

private fun LocalDate.utc() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.utcDay() = java.time.Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, d: LocalDate, modifier: Modifier = Modifier, onPick: (LocalDate) -> Unit) {
    val p = pal
    var open by remember { mutableStateOf(false) }
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(p.raised).clickable(onClickLabel = label) { open = true }.padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, style = Ty.ui(13, FontWeight.Medium).copy(color = p.ink2))
        Text("${weekday(d)} ${shortDay(d)} ${d.year}", Modifier.padding(top = 2.dp), style = Ty.mono(16, FontWeight.Medium).copy(color = p.ink))
    }
    if (open) {
        val st = rememberDatePickerState(initialSelectedDateMillis = d.utc())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextBtn("Done") { st.selectedDateMillis?.let { onPick(it.utcDay()) }; open = false } },
            dismissButton = { TextBtn("Cancel", color = p.ink2) { open = false } },
        ) { DatePicker(st) }
    }
}

@Composable
fun WeightSheet(onClose: () -> Unit) {
    val c = LocalContext.current
    val snack = LocalSnack.current
    var text by remember { mutableStateOf("") }
    val kg = text.replace(',', '.').toDoubleOrNull()
    val ok = kg != null && kg in 25.0..300.0
    val go = {
        val v = kg ?: 0.0
        onClose()
        snack.go {
            when (val r = LogWeight(c, c.sl.repo).run(mapOf("kg" to v))) {
                is ToolOut.Ok -> snack.say(r.text)
                is ToolOut.Fail -> snack.say("Couldn't save that weight")
                else -> Unit
            }
        }
    }
    Sheet(onClose) {
        SheetTitle("Log your weight")
        Field("Weight in kg", text, { text = it.take(6) }, Modifier.padding(top = 16.dp), keyboard = KeyboardType.Decimal, mono = true, ime = ImeAction.Done) { if (ok) go() }
        Btn("Save", Modifier.padding(top = 20.dp), go = true, enabled = ok) { go() }
    }
}
