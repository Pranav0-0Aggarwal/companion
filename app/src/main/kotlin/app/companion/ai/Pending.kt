package app.companion.ai

import android.app.Application
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import app.companion.core.Guard
import app.companion.core.Memo
import app.companion.core.Probe
import app.companion.core.Raw
import app.companion.core.Refine
import app.companion.core.Scorer
import app.companion.core.Source
import app.companion.core.Verdict
import app.companion.data.Item
import app.companion.data.Profile
import app.companion.data.Repo
import app.companion.ingest.SmsImport
import app.companion.sl
import app.companion.system.Live
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class Pending(
    private val app: Application,
    private val repo: Repo,
    private val refine: Refine,
    private val gov: Governor,
    private val scorer: Scorer,
    private val nux: NuExtractor,
) {
    private class Todo(val raw: Raw?, val state: String?)

    private val lock = Any()
    private val queue = LinkedHashMap<Long, Todo>()
    private val claimed = LinkedHashSet<Long>()

    private fun model() = Active.bert(app) != null || listOf(Models.base(app, Manifest.decide), File(Models.custom(app), Manifest.decide.file)).any { it.isFile }

    suspend fun submit(id: Long, raw: Raw, state: String) {
        if (!model()) return
        synchronized(lock) { queue[id] = Todo(raw, state) }
        val req = OneTimeWorkRequestBuilder<RefineWork>()
            .setInputData(workDataOf(ID to id))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        try {
            WorkManager.getInstance(app).enqueueUniqueWork("$NAME:$id", ExistingWorkPolicy.KEEP, req).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            synchronized(lock) { queue.remove(id) }
        }
    }

    internal suspend fun drain(id: Long, more: LongArray? = null) {
        delay(WINDOW)
        val todo = take(id, more)
        if (todo.isEmpty()) return
        val moved = LinkedHashSet<Long>()
        try {
            val now = Guard.take(vitals(app), todo.size, Active.bert(app) != null)
            if (now < todo.size) later(todo, now)
            val p = repo.profileNow()
            gov.hold {
                val raws = LinkedHashMap<Long, Raw>()
                val out = LinkedHashMap<Long, Verdict>()
                val memo = Memo(scorer)
                val probe = Probe()
                for ((i, t) in todo) {
                    currentCoroutineContext().ensureActive()
                    soft { t.raw ?: row(i) }?.let { raws[i] = it }
                }
                soft { refine.prime(raws.values, memo) }
                for ((i, raw) in raws) {
                    currentCoroutineContext().ensureActive()
                    soft { refine.run(raw, probe, memo) }?.let { out[i] = it }
                }
                for ((i, raw) in raws) {
                    if (!nux.on || i !in out || raw !in probe.asked) continue
                    currentCoroutineContext().ensureActive()
                    if (hot()) break
                    soft { refine.run(raw, nux, memo) }?.let { out[i] = it }
                }
                val it = todo.iterator()
                while (it.hasNext()) {
                    val (i, t) = it.next()
                    currentCoroutineContext().ensureActive()
                    val v = out[i]
                    if (v != null && soft { repo.refine(i, raws.getValue(i), v, p, t.state).moved } == true) moved += i
                    it.remove()
                }
            }
        } finally {
            release(todo)
            if (moved.isNotEmpty()) withContext(NonCancellable) { Live.refresh(app, moved) }
        }
    }

    private suspend fun later(todo: MutableMap<Long, Todo>, keep: Int) {
        val rest = todo.entries.drop(keep).associate { it.key to it.value }
        rest.keys.forEach(todo::remove)
        release(rest)
        val req = OneTimeWorkRequestBuilder<RefineWork>()
            .setInputData(workDataOf(IDS to rest.keys.take(MAX).toLongArray()))
            .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
            .setInitialDelay(Guard.RECHECK, TimeUnit.MILLISECONDS)
            .build()
        try {
            WorkManager.getInstance(app).enqueueUniqueWork("$NAME:later", ExistingWorkPolicy.APPEND_OR_REPLACE, req).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private fun hot() = vitals(app).let { it.thermal >= Guard.MODERATE || it.saver }

    private suspend fun <T> soft(f: suspend () -> T): T? = try {
        f()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun row(id: Long): Raw? {
        val i = repo.item(id) ?: return null
        return raw(app, i, repo.senders(listOf(id))[id], true)
    }

    private fun take(id: Long, more: LongArray?): MutableMap<Long, Todo> = synchronized(lock) {
        val out = LinkedHashMap(queue)
        queue.clear()
        (more ?: longArrayOf(id)).forEach { if (it !in out && it !in claimed) out[it] = Todo(null, null) }
        claimed.addAll(out.keys)
        while (claimed.size > MAX) claimed.remove(claimed.first())
        out
    }

    private fun release(left: Map<Long, Todo>) = synchronized(lock) {
        left.forEach { (i, t) ->
            queue.putIfAbsent(i, t)
            claimed.remove(i)
        }
    }

    companion object {
        internal const val ID = "id"
        internal const val IDS = "ids"
        private const val NAME = "refine"
        private const val WINDOW = 1_000L
        private const val MAX = 500

        fun raw(c: Context, i: Item, from: String?, exact: Boolean = false): Raw? {
            val src = Source.entries.firstOrNull { it.name == i.src } ?: return null
            val who = from ?: i.title
            fun find() = if (src == Source.Sms) SmsImport.find(c, who, i.at) else null
            val text = if (exact) find() ?: i.body else i.body ?: find()
            return Raw(src, who, "", text ?: return null, i.at)
        }
    }
}

class RefineWork(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        applicationContext.sl.pending.drain(inputData.getLong(Pending.ID, 0), inputData.getLongArray(Pending.IDS))
        Result.success()
    }
}
