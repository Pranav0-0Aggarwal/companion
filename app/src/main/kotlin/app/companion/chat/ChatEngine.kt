package app.companion.chat

import android.content.Context
import android.os.Process
import android.os.SystemClock
import app.companion.ai.ChatClient
import app.companion.ai.Chip
import app.companion.ai.Governor
import app.companion.ai.LowMemory
import app.companion.ai.Manifest
import app.companion.ai.Models
import app.companion.ai.Spec
import app.companion.ai.vitals
import app.companion.core.Card
import app.companion.core.ChatTemplate
import app.companion.core.Clarify
import app.companion.core.Convo
import app.companion.core.Decode
import app.companion.core.Guard
import app.companion.core.Json
import app.companion.core.ObjEnd
import app.companion.core.Prompt
import app.companion.core.Registry
import app.companion.core.Role
import app.companion.core.SayStream
import app.companion.core.Step
import app.companion.core.ToolOut
import app.companion.core.Turn
import app.companion.data.Repo
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface Out {
    data class Say(val delta: String) : Out
    data class Used(val tool: String) : Out
    data class Ask(val question: String) : Out
    data class Open(val route: String) : Out
    data class Show(val card: Card) : Out
    data class Fail(val why: String) : Out
    data object Done : Out
}

class Reply(val text: String, val ask: String?, val route: String?, val failed: String?)

class ChatEngine(private val c: Context, private val repo: Repo, private val gov: Governor) {
    private class Brain(val spec: Spec, val tpl: ChatTemplate)

    private val brains = listOf(Brain(Manifest.chat, ChatTemplate.Qwen))
    val foods = Foods(c, repo)
    val gate = Gate(repo)

    @Volatile private var sink: SendChannel<Out>? = null
    private val registry by lazy { Registry(ChatTools.of(c, repo, foods, gate, { sink?.trySend(Out.Open(it)) }, ::show)) }
    private val system by lazy { Prompt.system(registry.listing()) }
    private val grammar by lazy { registry.grammar() }
    private val hist = Convo()
    private val turn = Mutex()
    private val stamp = DateTimeFormatter.ofPattern("EEE yyyy-MM-dd HH:mm")
    private val zone get() = ZoneId.systemDefault()

    private fun brain() = if (Chip.nux) brains.firstOrNull { Models.file(c, it.spec) != null } else null

    fun ready() = brain() != null

    @Volatile private var shown = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var warming: Job? = null
    private var warmed = 0L

    private fun show(c: Card) {
        shown = true
        sink?.trySend(Out.Show(c))
    }

    fun ask(text: String, order: Long? = null, bg: Boolean = false, at: Pair<LocalDate, String?>? = null): Flow<Out> = direct(order, at) { run(text.take(MAX_TEXT), bg) }

    fun log(text: String, day: LocalDate, slot: String?, order: Long?): Flow<Out> = direct(order, day to slot) {
        foods.quiet(Clarify.spoken(text), foods.wenFor(day, slot), "chat", null, order)
        send(Out.Done)
    }

    fun dayCard(day: LocalDate): Flow<Out> = direct(null, null) {
        foods.day(day)
        send(Out.Done)
    }

    private fun direct(order: Long?, at: Pair<LocalDate, String?>?, block: suspend ProducerScope<Out>.() -> Unit): Flow<Out> = channelFlow {
        turn.withLock {
            sink = channel
            shown = false
            foods.order = order
            foods.pin = at
            foods.emit = ::show
            try {
                block()
            } finally {
                sink = null
                foods.order = null
                foods.pin = null
                foods.emit = null
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun reply(text: String, order: Long? = null, bg: Boolean = false): Reply {
        val say = StringBuilder()
        var ask: String? = null
        var route: String? = null
        var failed: String? = null
        ask(text, order, bg).collect {
            when (it) {
                is Out.Say -> say.append(it.delta)
                is Out.Ask -> ask = it.question
                is Out.Open -> route = it.route
                is Out.Fail -> failed = it.why
                else -> Unit
            }
        }
        return Reply(say.toString(), ask, route, failed)
    }

    fun reset() {
        hist.clear()
        foods.cancel()
        gate.clear()
    }

    fun warm() {
        val b = brain() ?: return
        if (warming?.isActive == true || Guard.hold(vitals(c)) != null) return
        warmed = SystemClock.elapsedRealtime()
        warming = scope.launch {
            try {
                abortable { hold ->
                    gov.run(b.spec, { ChatClient.open(c, Models.file(c, b.spec) ?: error("model"), threads()) }) { cl ->
                        hold(cl)
                        cl.warm(b.tpl.head(system), pre(), Process.THREAD_PRIORITY_BACKGROUND)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun cool() {
        if (SystemClock.elapsedRealtime() - warmed < WARM_MS) warming?.cancel()
    }

    private fun threads() = Guard.threads(vitals(c))

    private fun pre() = Guard.prefill(vitals(c))

    private fun said(s: String) = Json.write(mapOf("say" to s))

    private suspend fun ProducerScope<Out>.settle(text: String, out: ToolOut) {
        hist.add(Role.User, text)
        when (out) {
            is ToolOut.Ok -> {
                hist.add(Role.Assistant, said(out.text))
                if (!shown) send(Out.Say(out.text))
            }
            is ToolOut.Ask -> {
                hist.add(Role.Assistant, said(out.question))
                send(Out.Ask(out.question))
            }
            is ToolOut.Fail -> send(Out.Fail(out.why))
        }
        send(Out.Done)
    }

    private suspend fun CoroutineScope.abortable(block: suspend ((ChatClient) -> Unit) -> Boolean): Boolean {
        val held = AtomicReference<ChatClient?>()
        val watch = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                held.get()?.cancel()
            }
        }
        try {
            return block { held.set(it) }
        } finally {
            held.set(null)
            watch.cancel()
        }
    }

    private suspend fun ProducerScope<Out>.generate(b: Brain, turns: List<Turn>, strict: Boolean, bg: Boolean, spoke: StringBuilder): String? {
        val lead = if (strict) "" else Decode.LEAD
        val raw = StringBuilder(lead)
        val say = if (spoke.isEmpty()) SayStream().also { it.feed(lead) } else null
        val end = ObjEnd().also { it.feed(lead) }
        val prompt = b.tpl.render(system, turns, lead)
        val prio = if (bg) Process.THREAD_PRIORITY_BACKGROUND else Process.THREAD_PRIORITY_DEFAULT
        val ok = abortable { hold ->
            gov.run(b.spec, { ChatClient.open(c, Models.file(c, b.spec) ?: error("model"), threads()) }) { cl ->
                hold(cl)
                cl.run(prompt, b.tpl.head(system), if (strict) grammar else "", if (strict) MAX_TOKENS else Decode.FREE, threads(), pre(), TEMP, prio) { piece ->
                    raw.append(piece)
                    say?.feed(piece)?.takeIf { it.isNotEmpty() }?.let { spoke.append(it); trySend(Out.Say(it)) }
                    end.feed(piece)
                    isActive && !end.done()
                }
            }
        }
        ensureActive()
        return raw.toString().takeIf { ok }
    }

    private suspend fun ProducerScope<Out>.run(text: String, bg: Boolean) {
        foods.answer(text)?.let { return settle(text, it) }
        val b = brain() ?: return fail("model")
        val first = Turn(Role.User, Prompt.user(text, stamp.format(ZonedDateTime.now(zone))))
        val local = mutableListOf(first)
        var calls = 0
        var got = false
        var nudged = false
        val seen = mutableSetOf<Pair<String, Map<String, Any?>>>()
        while (true) {
            val turns = hist.fit(local) { b.tpl.render(system, it).length <= MAX_PROMPT }
            val spoke = StringBuilder()
            val d = try {
                Decode.run(registry) { strict -> generate(b, turns, strict, bg, spoke) }
            } catch (_: LowMemory) {
                return fail("memory")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: return fail("model")
            when (val s = d.step) {
                is Step.Say -> {
                    val rest = if (s.text.startsWith(spoke.toString())) s.text.substring(spoke.length) else ""
                    if (rest.isNotEmpty()) send(Out.Say(rest))
                    hist.add(first)
                    hist.add(Role.Assistant, d.raw)
                    return send(Out.Done)
                }
                is Step.Call -> {
                    if (!seen.add(s.tool.name to s.args)) {
                        if (nudged) return quiet(first, got)
                        nudged = true
                        local += Turn(Role.Assistant, d.raw)
                        local += Turn(Role.Tool, Prompt.ANSWER)
                        continue
                    }
                    if (++calls > MAX_CALLS) return quiet(first, got)
                    send(Out.Used(s.tool.name))
                    local += Turn(Role.Assistant, d.raw)
                    val r = try {
                        s.tool.run(s.args)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        ToolOut.Fail("tool failed")
                    }
                    when (r) {
                        is ToolOut.Ok -> {
                            got = true
                            local += Turn(Role.Tool, r.text.take(MAX_RESULT))
                        }
                        is ToolOut.Fail -> local += Turn(Role.Tool, "error: ${r.why}".take(MAX_RESULT))
                        is ToolOut.Ask -> {
                            hist.add(first)
                            hist.add(Role.Assistant, said(r.question))
                            send(Out.Ask(r.question))
                            return send(Out.Done)
                        }
                    }
                }
                is Step.Bad -> return fail("format")
            }
        }
    }

    private suspend fun ProducerScope<Out>.quiet(first: Turn, got: Boolean) {
        if (!got) return fail("steps")
        hist.add(first)
        send(Out.Done)
    }

    private suspend fun ProducerScope<Out>.fail(why: String) {
        send(Out.Fail(why))
        send(Out.Done)
    }

    private companion object {
        const val MAX_CALLS = 4
        const val MAX_TOKENS = 384
        const val WARM_MS = 10_000L
        const val MAX_PROMPT = 8000
        const val MAX_RESULT = 1200
        const val MAX_TEXT = 600
        const val TEMP = 0.2f
    }
}
