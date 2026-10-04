package app.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.companion.data.Profile
import app.companion.ui.Ty
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Field
import app.companion.ui.kit.Group
import app.companion.ui.pal

data class AboutYou(val first: String = "", val call: String? = null, val city: String = "", val pay: String = "", val budget: String = "")

fun Profile.about() = AboutYou(first, call.ifBlank { null }, city, payDay?.toString().orEmpty(), budget?.div(100)?.toString().orEmpty())

fun Profile.withAbout(a: AboutYou) = copy(
    first = a.first.trim(),
    call = (a.call ?: a.first).trim().takeIf { it != a.first.trim() }.orEmpty(),
    city = a.city.trim(),
    payDay = a.pay.toIntOrNull(),
    budget = a.budget.toLongOrNull()?.times(100),
)

fun digits(v: String, max: Int) = v.length <= max && v.all { it in '0'..'9' }

private fun validDay(v: String) = digits(v, 2) && (v.isEmpty() || v.toInt() in 1..31)

@Composable
fun AboutFields(a: AboutYou, set: (AboutYou) -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Field("First name", a.first, { set(a.copy(first = it.take(40))) }, hint = "Required")
        Field("What should I call you?", a.call ?: a.first, { set(a.copy(call = it.take(40))) })
        Field("City (optional)", a.city, { set(a.copy(city = it.take(40))) })
        Field("Pay day (optional)", a.pay, { if (validDay(it)) set(a.copy(pay = it)) }, keyboard = KeyboardType.Number, hint = "1 to 31")
        Field("Monthly budget in rupees (optional)", a.budget, { if (digits(it, 9)) set(a.copy(budget = it)) }, keyboard = KeyboardType.Number, hint = "40000")
    }
}

@Composable
fun ProfileForm(p: Profile, onSave: (AboutYou) -> Unit) {
    var a by remember { mutableStateOf(p.about()) }
    Group {
        AboutFields(a) { a = it }
        if (a.first.isNotBlank() && p.withAbout(a) != p) Btn("Save", Modifier.padding(start = 16.dp, bottom = 16.dp), go = true) { onSave(a) }
    }
}

@Composable
fun VipForm(saved: Profile, onSave: (String) -> Unit) {
    val p = pal
    var t by remember { mutableStateOf(saved.vip) }
    val v = t.lines().map(String::trim).filter(String::isNotEmpty).joinToString("\n")
    Group {
        Column(Modifier.padding(16.dp)) {
            Text("One name per line. Their WhatsApp and Instagram messages are always kept.", Modifier.padding(bottom = 8.dp), style = Ty.ui(12).copy(color = p.ink2))
            OutlinedTextField(
                value = t,
                onValueChange = { t = it.take(2000) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = RoundedCornerShape(12.dp),
                textStyle = Ty.ui(15).copy(color = p.ink),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = p.accent, unfocusedBorderColor = p.rule, focusedContainerColor = p.card, unfocusedContainerColor = p.card,
                ),
            )
            if (v != saved.vip) Btn("Save", Modifier.padding(top = 12.dp), go = true) { onSave(v) }
        }
    }
}
