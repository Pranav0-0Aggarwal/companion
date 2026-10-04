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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.companion.ai.Answer
import app.companion.ai.Answers
import app.companion.ai.Drill
import app.companion.ai.DrillBox
import app.companion.core.Edit
import app.companion.core.Query
import app.companion.core.Suggestion
import app.companion.sl
import app.companion.system.Cals
import app.companion.system.Plan
import app.companion.ui.Ty
import app.companion.ui.clock
import app.companion.ui.dateOf
import app.companion.ui.dayLabel
import app.companion.ui.inr
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Field
import app.companion.ui.kit.Group
import app.companion.ui.kit.Ic
import app.companion.ui.kit.MoneyRow
import app.companion.ui.kit.Motion
import app.companion.ui.kit.Roll
import app.companion.ui.kit.Rule
import app.companion.ui.kit.TextBtn
import app.companion.ui.kit.rememberHaptic
import app.companion.ui.pal
import app.companion.ui.shortDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

@Composable
fun AnswerCard(first: Answer, go: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = pal
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var a by remember(first) { mutableStateOf(first) }
    var editing by remember(first) { mutableStateOf(false) }
    var bad by remember(first) { mutableStateOf(false) }
    val edit = remember(first) { Edit(System.currentTimeMillis(), ZoneId.of("Asia/Kolkata")) }
    var vals by remember(a) { mutableStateOf(edit.values(a.query)) }
    val drill = { d: Drill? ->
        DrillBox.pending.value = d
        go("ledger")
    }
    Group(modifier.padding(bottom = 12.dp), raised = true) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 8.dp)) {
            Text("UNDERSTOOD AS", style = Ty.ui(11, FontWeight.Bold).copy(color = p.ink2, letterSpacing = 1.sp))
            Row(
                Modifier.padding(top = 8.dp).heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).background(p.accentBox)
                    .clickable(role = Role.Button, onClickLabel = if (editing) "Close edit" else "Edit what was understood") { editing = !editing }
                    .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(a.says, Modifier.weight(1f, fill = false), style = Ty.ui(14).copy(color = p.onAccentBox))
                Icon(if (editing) Ic.Close else Ic.Edit, null, Modifier.padding(start = 8.dp).size(16.dp), tint = p.onAccentBox)
            }
            AnimatedVisibility(editing, enter = expandVertically(Motion.soft()) + fadeIn(), exit = shrinkVertically(Motion.soft()) + fadeOut()) {
                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    edit.labels(a.query).forEachIndexed { i, l ->
                        Field(l, vals.getOrElse(i) { "" }, { v -> vals = vals.toMutableList().also { it[i] = v } }, Modifier.padding(top = 8.dp), mono = true)
                    }
                    if (bad) Text("Check the values", style = Ty.ui(13, FontWeight.Normal).copy(color = p.red))
                    Btn("Apply", Modifier.padding(top = 4.dp), go = true) {
                        val q = edit.apply(a.query, vals)
                        bad = q == null
                        if (q != null) {
                            scope.launch {
                                a = withContext(Dispatchers.Default) { Answers.run(q, c.sl.repo) }
                                editing = false
                            }
                        }
                    }
                }
            }
            a.total?.let { Roll(inr(it), Ty.mono(36, FontWeight.Bold).copy(color = p.ink), Modifier.padding(top = 14.dp)) }
            a.count?.let {
                Text(
                    if (it == 0) "Nothing found" else "$it ${if (it == 1) "payment" else "payments"}",
                    Modifier.padding(top = 2.dp),
                    style = Ty.ui(13, FontWeight.Normal).copy(color = p.ink2),
                )
            }
            a.proposal?.let { key(it) { Proposal(it) } }
        }
        a.lines.forEach { l ->
            Rule()
            MoneyRow(l.title, l.sub, inr(l.paise), false, time = shortDay(l.date), onClick = { drill(l.drill ?: a.drill) })
        }
        if (a.drill != null && a.count != 0) {
            Rule()
            TextBtn("Open in Ledger", Modifier.padding(start = 6.dp, top = 4.dp, bottom = 4.dp)) { drill(a.drill) }
        }
    }
}

@Composable
private fun Proposal(q: Query) {
    val p = pal
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    var done by remember { mutableStateOf(false) }
    val at = when (q) {
        is Query.CreateReminder -> q.at
        is Query.CreateEvent -> q.start
        else -> return
    }
    Text("${dayLabel(dateOf(at))} ${clock(at)}", Modifier.padding(top = 12.dp), style = Ty.mono(15).copy(color = p.ink))
    Btn(if (done) "Done" else "Confirm", Modifier.padding(top = 8.dp), go = !done, icon = if (done) Ic.Check else null) {
        if (!done) {
            done = true
            haptic(HapticFeedbackType.Confirm)
            scope.launch {
                when (q) {
                    is Query.CreateReminder -> Plan.remind(c, q.title, q.at)
                    is Query.CreateEvent -> withContext(Dispatchers.IO) { Cals.add(c, c.sl.repo.profileNow().cal, Suggestion.Cal(q.title, q.start, q.end, false, "")) }
                }
            }
        }
    }
}
