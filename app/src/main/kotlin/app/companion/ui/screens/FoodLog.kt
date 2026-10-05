package app.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.companion.core.Clarify
import app.companion.core.Meal
import app.companion.core.Wen
import app.companion.sl
import app.companion.ui.Ty
import app.companion.ui.dayLabel
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Chip
import app.companion.ui.kit.Field
import app.companion.ui.kit.Ic
import app.companion.ui.kit.LocalSnack
import app.companion.ui.kit.ToolButton
import app.companion.ui.pal
import app.companion.ui.rememberVoice
import app.companion.ui.today
import app.companion.ui.zone
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LogReq(val day: LocalDate, val slot: String? = null, val order: Long? = null)

private suspend fun log(c: android.content.Context, text: String, r: LogReq, slot: String?): String = withContext(Dispatchers.Default) {
    val chat = c.sl.chat
    val foods = chat.foods
    val said = if (chat.ready()) {
        val on = if (r.day == today()) "" else " on ${dayLabel(r.day)}"
        val ask = when {
            foods.pending -> text
            r.order != null -> "I ordered: $text"
            else -> "I had this${slot?.let { " for $it" }.orEmpty()}$on: $text"
        }
        chat.reply(ask, r.order).let { it.ask ?: it.text.ifBlank { null } }
    } else {
        val meal = Meal.entries.firstOrNull { it.label == slot } ?: Meal.of(LocalTime.now())
        val w = if (r.day == today()) foods.wen(slot.orEmpty()) else Wen(r.day, meal, r.day.atTime(foods.times()[meal] ?: LocalTime.NOON).atZone(zone()).toInstant().toEpochMilli())
        foods.quiet(Clarify.spoken(text), w, if (r.order != null) "order" else "chat", null, r.order)
    }
    said ?: "I couldn't log that."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSheet(r: LogReq, onClose: () -> Unit) {
    val p = pal
    val c = LocalContext.current
    val snack = LocalSnack.current
    var text by remember { mutableStateOf("") }
    var slot by remember { mutableStateOf(r.slot) }
    val voice = rememberVoice { t -> t?.let { text = (text + " " + it).trim() } }
    val go = {
        val t = text.trim()
        onClose()
        snack.go { snack.say(log(c, t, r, slot)) }
    }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = p.bg, contentColor = p.ink) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 24.dp).padding(bottom = 20.dp)) {
            Text(if (r.order != null) "Change ${r.slot ?: "meal"}" else "Log a meal", style = Ty.ui(22, FontWeight.Bold).copy(color = p.ink))
            Text(if (r.day == today()) "Today" else dayLabel(r.day), Modifier.padding(top = 4.dp), style = Ty.ui(14, FontWeight.Normal).copy(color = p.ink2))
            if (r.order == null) {
                FlowRow(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Meal.entries.forEach { m -> Chip(m.label.cap(), slot == m.label) { slot = if (slot == m.label) null else m.label } }
                }
            }
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Field("What you had", text, { text = it.take(300) }, Modifier.weight(1f), ime = ImeAction.Done) { if (text.isNotBlank()) go() }
                if (voice != null) ToolButton(Ic.Mic, "Say it", voice)
            }
            Btn("Log", Modifier.padding(top = 20.dp), go = true, enabled = text.isNotBlank()) { go() }
        }
    }
}
