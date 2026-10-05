package app.companion.chat

import android.content.Context
import android.os.Process
import app.companion.ai.ChatClient
import app.companion.ai.Chip
import app.companion.ai.Governor
import app.companion.ai.LowMemory
import app.companion.ai.Manifest
import app.companion.ai.Models
import app.companion.ai.Spec
import app.companion.ai.vitals
import app.companion.core.ChatTemplate
import app.companion.core.Convo
import app.companion.core.Guard
import app.companion.core.Json
import app.companion.core.Prompt
import app.companion.core.Registry
import app.companion.core.Role
import app.companion.core.SayStream
import app.companion.core.Step
import app.companion.core.ToolOut
import app.companion.core.Turn
import app.companion.data.Repo
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface Out {
    data class Say(val delta: String) : Out
    data class Used(val tool: String) : Out
    data class Ask(val question: String) : Out
    data class Open(val route: String) : Out
    data class Fail(val why: String) : Out
    data object Done : Out
}

class Reply(val text: String, val ask: String?, val route: String?, val failed: String?)

class ChatEngine(private val c: Context, private val repo: Repo, private val gov: Governor) {
    private class Brain(val spec: Spec, val tpl: ChatTemplate)

    private val brains = listOf(Brain(Manifest.chat, ChatTemplate.Qwen), Brain(Manifest.chatLfm, ChatTemplate.Lfm))
    val foods = Foods(c, repo)

    @Volatile private var sink: SendChannel<Out>? = null
    private val registry by lazy { Registry(ChatTools.of(c, repo, foods) { sink?.trySend(Out.Open(it)) }) }
    private val system by lazy { Prompt.system(registry.listing()) }
    private val grammar by lazy { registry.grammar() }
    private val hist = Convo()
    private val turn = Mutex()
    private val stamp = DateTimeFormatter.ofPattern("EEE yyyy-MM-dd HH:mm")
    private val zone get() = ZoneId.systemDefault()

    private fun brain() = if (Chip.nux) brains.firstOrNull { Models.file(c, it.spec) != null } else null

    fun ready() = brain() != null

    fun ask(text: String, order: Long? = null, bg: Boolean = false): Flow<Out> = channelFlow {
        turn.withLock {
            sink = channel
            foods.order = order
            try {
                run(text.take(MAX_TEXT), bg)
            } finally {
                sink = null
                foods.order = null
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
    }

    private fun threads() = Guard.threads(vitals(c))

    private fun said(s: String) = Json.write(mapOf("say" to s))

    private suspend fun ProducerScope<Out>.settle(text: String, out: ToolOut) {
        hist.add(Role.User, text)
        when (out) {
            is ToolOut.Ok -> {
                hist.add(Role.Assistant, said(out.text))
                send(Out.Say(out.text))
            }
            is ToolOut.Ask -> {
                hist.add(Role.Assistant, said(out.question))
                send(Out.Ask(out.question))
            }
            is ToolOut.Fail -> send(Out.Fail(out.why))
        }
        send(Out.Done)
    }

    private suspend fun ProducerScope<Out>.run(text: String, bg: Boolean) {
        foods.answer(text)?.let { return settle(text, it) }
        val b = brain() ?: return fail("model")
        val first = Turn(Role.User, Prompt.user(text, stamp.format(ZonedDateTime.now(zone))))
        val local = mutableListOf(first)
        var calls = 0
        var bad = 0
        while (true) {
            val raw = StringBuilder()
            val stream = SayStream()
            val prompt = b.tpl.render(system, hist.turns + local)
            val ok = try {
                gov.run(b.spec, { ChatClient.open(c, Models.file(c, b.spec) ?: error("model"), threads()) }) { cl ->
                    cl.run(prompt, grammar, MAX_TOKENS, threads(), TEMP, if (bg) Process.THREAD_PRIORITY_BACKGROUND else Process.THREAD_PRIORITY_DEFAULT) { piece ->
                        raw.append(piece)
                        stream.feed(piece).takeIf { it.isNotEmpty() }?.let { trySend(Out.Say(it)) }
                        isActive
                    }
                }
            } catch (_: LowMemory) {
                return fail("memory")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (!ok) return fail("model")
            val out = raw.toString().trim()
            when (val s = registry.parse(out)) {
                is Step.Say -> {
                    hist.add(first)
                    hist.add(Role.Assistant, out)
                    return send(Out.Done)
                }
                is Step.Call -> {
                    if (++calls > MAX_CALLS) return fail("steps")
                    send(Out.Used(s.tool.name))
                    local += Turn(Role.Assistant, out)
                    val r = try {
                        s.tool.run(s.args)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        ToolOut.Fail("tool failed")
                    }
                    when (r) {
                        is ToolOut.Ok -> local += Turn(Role.Tool, r.text.take(MAX_RESULT))
                        is ToolOut.Fail -> local += Turn(Role.Tool, "error: ${r.why}".take(MAX_RESULT))
                        is ToolOut.Ask -> {
                            hist.add(first)
                            hist.add(Role.Assistant, said(r.question))
                            send(Out.Ask(r.question))
                            return send(Out.Done)
                        }
                    }
                }
                is Step.Bad -> if (++bad > 1) return fail("format")
            }
        }
    }

    private suspend fun ProducerScope<Out>.fail(why: String) {
        send(Out.Fail(why))
        send(Out.Done)
    }

    private companion object {
        const val MAX_CALLS = 4
        const val MAX_TOKENS = 384
        const val MAX_RESULT = 1200
        const val MAX_TEXT = 600
        const val TEMP = 0.2f
    }
}
