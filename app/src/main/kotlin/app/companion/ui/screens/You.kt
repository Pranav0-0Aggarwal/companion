package app.companion.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.chat.LogWeight
import app.companion.core.DocKind
import app.companion.core.Privacy
import app.companion.core.Spark
import app.companion.core.ToolOut
import app.companion.data.DocRow
import app.companion.data.Profile
import app.companion.sl
import app.companion.core.Alerts
import app.companion.ui.Ty
import app.companion.ui.dateOf
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Field
import app.companion.ui.kit.Group
import app.companion.ui.kit.Ic
import app.companion.ui.kit.Ink
import app.companion.ui.kit.LocalSnack
import app.companion.ui.kit.PassLine
import app.companion.ui.kit.Rule
import app.companion.ui.kit.Screen
import app.companion.ui.kit.Section
import app.companion.ui.kit.Spark
import app.companion.ui.kit.Stamp
import app.companion.ui.kit.Tone
import app.companion.ui.kit.empty
import app.companion.ui.kit.part
import app.companion.ui.pal
import app.companion.ui.rememberNow
import app.companion.ui.shortDay
import app.companion.ui.today
import app.companion.ui.zone
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.launch

private fun DocRow.date() = expires?.let(::dateOf)

@Composable
fun YouScreen(go: (String) -> Unit) {
    val p = pal
    val c = LocalContext.current
    val repo = c.sl.repo
    val prof by repo.profile.collectAsStateWithLifecycle(Profile())
    val trips by remember { repo.life.tripFlow() }.collectAsStateWithLifecycle(emptyList())
    val docs by remember { repo.docs.list() }.collectAsStateWithLifecycle(emptyList())
    val weights by remember { repo.life.weightFlow(60) }.collectAsStateWithLifecycle(emptyList())
    val day = today()
    val read by remember(day) { repo.read(Alerts.dayStart(System.currentTimeMillis(), zone()), day.toEpochDay()) }.collectAsStateWithLifecycle(0)
    var weigh by remember { mutableStateOf(false) }
    val last = weights.firstOrNull()
    val pts = remember(weights, day) { Spark.window(weights.map { LocalDate.ofEpochDay(it.day) to it.kg }, day) }
    val delta = if (pts.size >= 2) pts.last().second - pts.first().second else null
    val next = docs.mapNotNull { it.date() }.filter { !it.isBefore(day) }.minOrNull()
    Screen("You", "Everything stays on this phone", tall = false) {
        item(key = "profile") {
            Box(Modifier.padding(top = 8.dp).part(p, true, true)) {
                PassLine(
                    prof.name.ifBlank { "Add your name" },
                    prof.payDay?.let { "Payday on the ${ord(it)}" } ?: "Set your payday in Settings",
                    lead = Ic.User, onClick = { go("settings") },
                    trailing = { Icon(Ic.Right, null, tint = p.ink2) },
                )
            }
        }
        item(key = "things") {
            Column(Modifier.padding(top = 16.dp)) {
                Box(Modifier.part(p, true, false)) {
                    PassLine("Trips", if (trips.isEmpty()) "No trips yet" else "${trips.size} ${if (trips.size == 1) "trip" else "trips"}", lead = Ic.Trip, onClick = { go("trips") }, trailing = { Icon(Ic.Right, null, tint = p.ink2) })
                }
                Box(Modifier.part(p, false, false)) {
                    PassLine(
                        "Vault",
                        if (docs.isEmpty()) "Nothing saved yet" else "${docs.size} ${if (docs.size == 1) "document" else "documents"}${next?.let { " · next expiry ${shortDay(it)}" }.orEmpty()}",
                        lead = Ic.Vault, onClick = { go("vault") }, trailing = { Icon(Ic.Right, null, tint = p.ink2) },
                    )
                }
                Box(Modifier.part(p, false, true)) {
                    PassLine("What I've learned", "Rules, merchants and corrections", lead = Ic.Sparkle, onClick = { go("learn") }, trailing = { Icon(Ic.Right, null, tint = p.ink2) })
                }
            }
        }
        item(key = "weight") {
            Box(Modifier.padding(top = 16.dp).part(p, true, true)) {
                PassLine(
                    "Weight",
                    if (last == null) "Tap to log your weight" else listOfNotNull(delta?.let { String.format(Locale.US, "%+.1f kg in 4 weeks", it) }, shortDay(LocalDate.ofEpochDay(last.day))).joinToString(" · "),
                    lead = Ic.Scale, onClick = { weigh = true },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (pts.size >= 2) Spark(pts.map { it.second }, Modifier.size(72.dp, 32.dp), "Weight over four weeks")
                            if (last != null) Text(String.format(Locale.US, "%.1f", last.kg), Modifier.padding(start = 12.dp), style = Ty.mono(16, FontWeight.SemiBold).copy(color = p.ink), maxLines = 1)
                        }
                    },
                )
            }
        }
        item(key = "privacy") {
            Box(Modifier.padding(top = 16.dp).part(p, true, true)) {
                PassLine("Privacy", Privacy.line(read), lead = Ic.Shield, tone = Tone.Green)
            }
        }
        item(key = "settings") {
            Box(Modifier.padding(top = 16.dp).part(p, true, true)) {
                PassLine("Settings", "Sources, alerts, lock and data", lead = Ic.Settings, onClick = { go("settings") }, trailing = { Icon(Ic.Right, null, tint = p.ink2) })
            }
        }
    }
    if (weigh) WeightSheet { weigh = false }
}

@Composable
fun VaultScreen(back: () -> Unit) {
    val p = pal
    val c = LocalContext.current
    val docs by remember { c.sl.repo.docs.list() }.collectAsStateWithLifecycle(emptyList())
    val now = today()
    Screen("Vault", if (docs.isEmpty()) "Nothing saved yet" else "${docs.size} ${if (docs.size == 1) "document" else "documents"} · numbers stay masked", back = back, nav = false, tall = false) {
        if (docs.isEmpty()) empty(Ic.Vault, "Nothing saved yet", "Ask Companion to save a document, like your insurance number and its expiry.")
        itemsIndexed(docs, key = { _, d -> "d${d.id}" }) { k, d ->
            val exp = d.date()
            val days = exp?.let { ChronoUnit.DAYS.between(now, it).toInt() }
            Box(Modifier.animateItem().part(p, k == 0, k == docs.lastIndex)) {
                PassLine(
                    d.title, listOfNotNull(DocKind.entries.firstOrNull { it.name == d.kind }?.label, d.mask, exp?.let { "expires ${shortDay(it)} ${it.year}" }).joinToString(" · "),
                    lead = Ic.Vault, tone = Tone.Plain, lines = 1,
                    trailing = { if (days != null && days <= 30) Stamp(if (days < 0) "EXPIRED" else "SOON", ink = if (days < 0) Ink.Red else Ink.Amber) },
                )
            }
        }
    }
}

@Composable
fun LearnScreen(back: () -> Unit) {
    Screen("What I've learned", "Corrections and rules, kept on this phone", back = back, nav = false, tall = false) {
        item(key = "learn") { LearnSettings() }
    }
}
