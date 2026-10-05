package app.companion.chat

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.companion.Services
import app.companion.ai.Answer
import app.companion.ai.Answers
import app.companion.core.Card
import app.companion.core.ChatOpts
import app.companion.core.Opt
import app.companion.core.Pend
import app.companion.data.Item
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogReq(val day: LocalDate, val slot: String? = null, val order: Long? = null)

sealed class Msg(val id: Long) {
    class User(id: Long, val text: String) : Msg(id)

    class Bot(id: Long) : Msg(id) {
        var text by mutableStateOf("")
    }

    class Rule(id: Long, val answer: Answer) : Msg(id)

    class Res(id: Long, val card: Card) : Msg(id)

    class Pick(id: Long, val question: String, val opts: List<Opt>) : Msg(id) {
        var done by mutableStateOf(false)
    }

    class Hits(id: Long, val items: List<Item>) : Msg(id)

    class Need(id: Long) : Msg(id)

    class Note(id: Long, val text: String) : Msg(id)

    class Act(id: Long, val key: Long, val text: String) : Msg(id) {
        var at by mutableStateOf(Pend.Wait)
        var said by mutableStateOf("")
    }
}

@Stable
class ChatSession(private val sl: Services) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var n = 0L

    val msgs = mutableStateListOf<Msg>()
    var busy by mutableStateOf(false)
        private set
    var typing by mutableStateOf(false)
        private set
    var working by mutableStateOf(false)
        private set
    var ctx by mutableStateOf<LogReq?>(null)

    val pending get() = (msgs.lastOrNull() as? Msg.Pick)?.takeIf { !it.done }

    private fun id() = ++n

    fun send(raw: String, go: (String) -> Unit = {}) {
        val text = raw.trim()
        if (text.isEmpty()) return
        (msgs.lastOrNull() as? Msg.Pick)?.done = true
        msgs += Msg.User(id(), text)
        run(go) { turn(text, go) }
    }

    fun today(day: LocalDate = LocalDate.now()) {
        (msgs.lastOrNull() as? Msg.Pick)?.done = true
        msgs += Msg.User(id(), "What did I eat today?")
        run({}) { collect(sl.chat.dayCard(day), {}) }
    }

    fun warm() = sl.chat.warm()

    fun cool() = sl.chat.cool()

    fun stop() {
        job?.cancel()
        busy = false
        idle()
    }

    fun confirm(m: Msg.Act, offer: (Undo) -> Unit) {
        if (m.at != Pend.Wait) return
        m.at = Pend.Run
        scope.launch {
            val d = sl.chat.gate.confirm(m.key)
            m.at = if (d?.ok == true) Pend.Done else Pend.Fail
            m.said = d?.said ?: "This is no longer available."
            d?.undos?.forEach(offer)
        }
    }

    fun cancel(m: Msg.Act) {
        if (m.at == Pend.Wait && sl.chat.gate.cancel(m.key)) m.at = Pend.Cancel
    }

    private fun idle() {
        typing = false
        working = false
    }

    fun reset() {
        stop()
        msgs.clear()
        ctx = null
        sl.chat.reset()
    }

    private fun run(go: (String) -> Unit, block: suspend () -> Unit) {
        val prev = job
        prev?.cancel()
        busy = true
        typing = true
        val me = scope.launch(start = CoroutineStart.LAZY) {
            prev?.join()
            try {
                block()
            } finally {
                if (job === coroutineContext[Job]) {
                    busy = false
                    idle()
                }
            }
        }
        job = me
        me.start()
    }

    private suspend fun turn(text: String, go: (String) -> Unit) {
        val c = ctx
        val chat = sl.chat
        val held = chat.foods.pending
        if (c == null && !held) {
            val answers = withContext(Dispatchers.Default) {
                sl.planner.plan(text, System.currentTimeMillis()).takeIf { it.explicit }?.queries.orEmpty().map { Answers.run(it, sl.repo) }
            }
            if (answers.isNotEmpty()) {
                answers.forEach { msgs += Msg.Rule(id(), it) }
                return
            }
            if (!chat.ready()) {
                val hits = withContext(Dispatchers.Default) { sl.repo.search(text).first() }
                if (hits.isNotEmpty()) msgs += Msg.Hits(id(), hits) else msgs += Msg.Note(id(), "I can answer questions about your spending, bills and plans. Try \"spent this week\" or \"bills due this week\".")
                msgs += Msg.Need(id())
                return
            }
        }
        if (c != null && !held && !chat.ready()) return collect(chat.log(text, c.day, c.slot, c.order), go)
        val ask = if (c != null && !held) "I had this${c.slot?.let { " for $it" }.orEmpty()}: $text" else text
        collect(chat.ask(ask, c?.order, at = c?.takeIf { !held }?.let { it.day to it.slot }), go)
    }

    private suspend fun collect(flow: Flow<Out>, go: (String) -> Unit) {
        var bot: Msg.Bot? = null
        flow.collect { o ->
            when (o) {
                is Out.Say -> {
                    idle()
                    val b = bot ?: Msg.Bot(id()).also { msgs += it; bot = it }
                    b.text += o.delta
                }
                is Out.Used -> {
                    typing = true
                    working = true
                    bot = null
                }
                is Out.Show -> {
                    idle()
                    val c = o.card
                    msgs += if (c is Card.Confirm) Msg.Act(id(), c.key, c.text) else Msg.Res(id(), c)
                    if (c is Card.Meal) ctx = null
                }
                is Out.Ask -> {
                    idle()
                    msgs += Msg.Pick(id(), o.question, ChatOpts.of(o.question))
                }
                is Out.Open -> go(o.route)
                is Out.Fail -> {
                    idle()
                    when (o.why) {
                        "model" -> msgs += Msg.Need(id())
                        "memory" -> msgs += Msg.Note(id(), "Not enough free memory for the chat model right now. Try again in a moment.")
                        else -> msgs += Msg.Note(id(), if (' ' in o.why) o.why else "I couldn't work that out. Try saying it another way.")
                    }
                }
                Out.Done -> idle()
            }
        }
    }
}
