package app.companion.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.companion.ai.Answer
import app.companion.ai.Drill
import app.companion.ai.DrillBox
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
import app.companion.ui.kit.Group
import app.companion.ui.kit.Rule
import app.companion.ui.pal
import app.companion.ui.shortDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AnswerCard(a: Answer, go: (String) -> Unit) {
    val p = pal
    val drill = { d: Drill? ->
        DrillBox.pending.value = d
        go("ledger")
    }
    Group(Modifier.padding(bottom = 12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("${a.title.uppercase()} · ${a.guide}", style = Ty.mono(10, FontWeight.Bold).copy(color = p.ink2, letterSpacing = 0.8.sp))
            a.total?.let {
                Text(inr(it), Modifier.padding(top = 6.dp), style = Ty.mono(30, FontWeight.ExtraBold).copy(color = p.ink))
            }
            a.count?.let {
                Text(if (it == 0) "Nothing found" else "$it ${if (it == 1) "payment" else "payments"}", Modifier.padding(top = 2.dp), style = Ty.mono(12).copy(color = p.ink2))
            }
            a.proposal?.let { Proposal(it) }
        }
        a.lines.forEach { l ->
            Rule()
            Row(Modifier.fillMaxWidth().clickable { drill(l.drill ?: a.drill) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(shortDay(l.date), Modifier.padding(end = 12.dp), style = Ty.mono(12).copy(color = p.ink2))
                Column(Modifier.weight(1f)) {
                    Text(l.title, style = Ty.ui(14).copy(color = p.ink), maxLines = 1)
                    if (l.sub.isNotEmpty()) Text(l.sub, style = Ty.mono(10).copy(color = p.ink2), maxLines = 1)
                }
                Text(inr(l.paise), style = Ty.mono(13, FontWeight.Bold).copy(color = p.ink), textAlign = TextAlign.End)
            }
        }
        if (a.drill != null && a.count != 0) {
            Column(Modifier.padding(16.dp)) { Btn("Open in Ledger") { drill(a.drill) } }
        }
    }
}

@Composable
private fun Proposal(q: Query) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var done by remember { mutableStateOf(false) }
    val at = when (q) {
        is Query.CreateReminder -> q.at
        is Query.CreateEvent -> q.start
        else -> return
    }
    Text("${dayLabel(dateOf(at))} ${clock(at)}", Modifier.padding(top = 4.dp), style = Ty.mono(12).copy(color = pal.ink2))
    Btn(if (done) "Done" else "Confirm", Modifier.padding(top = 10.dp), go = !done) {
        if (!done) {
            done = true
            scope.launch {
                when (q) {
                    is Query.CreateReminder -> Plan.remind(c, q.title, q.at)
                    is Query.CreateEvent -> withContext(Dispatchers.IO) { Cals.add(c, c.sl.repo.profileNow().cal, Suggestion.Cal(q.title, q.start, q.end, false, "")) }
                }
            }
        }
    }
}
