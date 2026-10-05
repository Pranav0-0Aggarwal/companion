package app.companion.ui.cover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.companion.Services
import app.companion.ai.Answer
import app.companion.ai.Answers
import app.companion.sl
import app.companion.ui.inr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Said(val head: String, val lines: List<String>)

@Stable
class Say(private val sl: Services, private val scope: CoroutineScope) {
    var said by mutableStateOf<Said?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    fun run(q: String) {
        if (q.isBlank() || busy) return
        busy = true
        said = null
        scope.launch {
            said = withContext(Dispatchers.Default) { answer(q) }
            busy = false
        }
    }

    private fun line(a: Answer) = Said(a.total?.takeIf { a.query !is app.companion.core.Query.CreateReminder }?.let(::inr) ?: a.says, listOfNotNull(a.says.takeIf { a.total != null }) + a.lines.take(3).map { "${it.title} ${inr(it.paise)}" })

    private suspend fun answer(q: String): Said {
        val plan = sl.planner.plan(q, System.currentTimeMillis())
        plan.takeIf { it.explicit }?.queries.orEmpty().firstOrNull()?.let { return line(Answers.run(it, sl.repo)) }
        if (sl.chat.ready()) sl.chat.reply(q).let { r -> (r.ask ?: r.text.ifBlank { null })?.let { return Said(it, emptyList()) } }
        return Said("I couldn't work that out", listOf("Try \"spent today\" or \"bills due this week\"."))
    }
}

@Composable
fun rememberSay(): Say {
    val sl = LocalContext.current.sl
    val scope = rememberCoroutineScope()
    return remember { Say(sl, scope) }
}
