package app.companion.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.data.Task
import app.companion.sl
import app.companion.system.Plan
import app.companion.ui.Ty
import app.companion.ui.clock
import app.companion.ui.dateOf
import app.companion.ui.dayLabel
import app.companion.ui.kit.Group
import app.companion.ui.kit.Ic
import app.companion.ui.kit.Rule
import app.companion.ui.kit.Screen
import app.companion.ui.kit.Section
import app.companion.ui.kit.ToolButton
import app.companion.ui.pal
import kotlinx.coroutines.launch

@Composable
fun PlanScreen(back: () -> Unit, sync: (() -> Unit)? = null) {
    val c = LocalContext.current
    val tasks by c.sl.repo.tasks.collectAsStateWithLifecycle(emptyList())
    val capture = LocalCapture.current
    val scope = rememberCoroutineScope()
    val open = tasks.filter { !it.done }
    val done = tasks.filter { it.done }.take(10)
    Screen(
        "Plan", if (open.isEmpty()) "Nothing open" else "${open.size} open",
        tools = {
            ToolButton(Ic.Back, "Back", back)
            if (sync != null) ToolButton(Ic.Plan, "Sync Google Tasks", sync)
            ToolButton(Ic.Add, "New reminder") { capture("") }
        },
    ) {
        if (open.isNotEmpty()) {
            item { Section("Open") }
            item { Group { open.forEachIndexed { i, t -> if (i > 0) Rule(); TaskRow(t, { scope.launch { Plan.finish(c, t.id) } }) { scope.launch { Plan.remove(c, t.id) } } } } }
        }
        if (done.isNotEmpty()) {
            item { Section("Done") }
            item { Group { done.forEachIndexed { i, t -> if (i > 0) Rule(); TaskRow(t, { scope.launch { Plan.reopen(c, t.id) } }) { scope.launch { Plan.remove(c, t.id) } } } } }
        }
    }
}

@Composable
private fun TaskRow(t: Task, toggle: () -> Unit, remove: () -> Unit) {
    val p = pal
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).border(2.dp, if (t.done) p.settled else p.rule, CircleShape).clickable(onClick = toggle), contentAlignment = Alignment.Center) {
            if (t.done) Icon(Ic.Check, "Reopen", Modifier.size(14.dp), tint = p.settled)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                t.title,
                style = Ty.ui(14).copy(color = if (t.done) p.ink2 else p.ink, textDecoration = if (t.done) TextDecoration.LineThrough else null),
            )
            val sub = listOfNotNull(
                t.remindAt?.let { "${dayLabel(dateOf(it))} ${clock(it)}" },
                t.repeat,
                if (t.gid != null) "Google" else null,
            ).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, Modifier.padding(top = 2.dp), style = Ty.mono(11, FontWeight.Medium).copy(color = p.ink2))
        }
        Icon(Ic.Close, "Delete", Modifier.size(20.dp).clickable(onClick = remove), tint = p.ink2)
    }
}
