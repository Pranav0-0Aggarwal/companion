package app.companion.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.companion.data.Profile
import app.companion.ui.Ty
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Field
import app.companion.ui.kit.Group
import app.companion.ui.kit.Ic
import app.companion.ui.kit.Motion
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
fun AboutFields(a: AboutYou, set: (AboutYou) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Field("First name", a.first, { set(a.copy(first = it.take(40))) })
        Field("What should I call you?", a.call ?: a.first, { set(a.copy(call = it.take(40))) })
        Field("City (optional)", a.city, { set(a.copy(city = it.take(40))) })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Pay day", a.pay, { if (validDay(it)) set(a.copy(pay = it)) }, Modifier.weight(1f), keyboard = KeyboardType.Number, mono = true, hint = "1 to 31")
            Field("Budget ₹", a.budget, { if (digits(it, 9)) set(a.copy(budget = it)) }, Modifier.weight(1.4f), keyboard = KeyboardType.Number, mono = true, hint = "Monthly, optional")
        }
    }
}

@Composable
fun ProfileCard(p: Profile, onSave: (AboutYou) -> Unit) {
    val c = pal
    var a by remember { mutableStateOf(p.about()) }
    var open by remember { mutableStateOf(false) }
    val name = p.name.ifBlank { p.first }
    Group(Modifier.padding(top = 4.dp)) {
        Row(
            Modifier.clickable(onClickLabel = if (open) "Close profile" else "Edit profile") { open = !open }.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(56.dp).background(c.accentBox, CircleShape), contentAlignment = Alignment.Center) {
                if (name.isNotBlank()) {
                    Text(name.take(1).uppercase(), style = Ty.ui(24, FontWeight.Bold).copy(color = c.onAccentBox))
                } else {
                    Icon(Ic.User, null, Modifier.size(26.dp), tint = c.onAccentBox)
                }
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(name.ifBlank { "Your profile" }, style = Ty.ui(18, FontWeight.Bold).copy(color = c.ink))
                Text(listOfNotNull(p.city.ifBlank { null }, p.payDay?.let { "payday on the $it" }).joinToString(" · ").ifEmpty { "Name, city, payday and budget" }, style = Ty.ui(13, FontWeight.Normal).copy(color = c.ink2))
            }
            val r by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, Motion.soft(), label = "chev")
            Icon(Ic.Down, null, Modifier.size(20.dp).graphicsLayer { rotationZ = r }, tint = c.ink2)
        }
        AnimatedVisibility(open, enter = expandVertically(Motion.soft()) + fadeIn(), exit = shrinkVertically(Motion.soft()) + fadeOut()) {
            Column {
                AboutFields(a, { a = it }, Modifier.padding(top = 0.dp))
                if (a.first.isNotBlank() && p.withAbout(a) != p) {
                    Btn("Save", Modifier.padding(start = 16.dp, bottom = 16.dp), go = true) {
                        onSave(a)
                        open = false
                    }
                }
            }
        }
    }
}

@Composable
fun VipForm(saved: Profile, onSave: (String) -> Unit) {
    val p = pal
    var t by remember { mutableStateOf(saved.vip) }
    val v = t.lines().map(String::trim).filter(String::isNotEmpty).joinToString("\n")
    Group {
        Column(Modifier.padding(16.dp)) {
            Text("One name per line. Their WhatsApp and Instagram messages are always kept.", Modifier.padding(bottom = 10.dp), style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2))
            Field("Names", t, { t = it.take(2000) }, lines = 3)
            if (v != saved.vip) Btn("Save", Modifier.padding(top = 12.dp), go = true) { onSave(v) }
        }
    }
}
