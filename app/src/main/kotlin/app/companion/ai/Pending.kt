package app.companion.ai

import android.app.Application
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import app.companion.core.Raw
import app.companion.core.Refine
import app.companion.core.Source
import app.companion.data.Item
import app.companion.data.Profile
import app.companion.data.Repo
import app.companion.ingest.SmsImport
import app.companion.sl
import app.companion.system.Live
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class Pending(private val app: Application, private val repo: Repo, private val refine: Refine, private val gov: Governor) {
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

    internal suspend fun drain(id: Long) {
        delay(WINDOW)
        val todo = take(id)
        if (todo.isEmpty()) return
        var changed = false
        try {
            val p = repo.profileNow()
            gov.hold {
                val it = todo.iterator()
                while (it.hasNext()) {
                    val (i, t) = it.next()
                    currentCoroutineContext().ensureActive()
                    changed = one(i, t, p) || changed
                    it.remove()
                }
            }
        } finally {
            release(todo)
            if (changed) withContext(NonCancellable) { Live.refresh(app) }
        }
    }

    private suspend fun one(id: Long, t: Todo, p: Profile): Boolean = try {
        val raw = t.raw ?: row(id)
        if (raw == null) false else repo.refine(id, raw, refine.run(raw), p, t.state).moved
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    private suspend fun row(id: Long): Raw? {
        val i = repo.item(id) ?: return null
        return raw(app, i, repo.senders(listOf(id))[id], true)
    }

    private fun take(id: Long): MutableMap<Long, Todo> = synchronized(lock) {
        val out = LinkedHashMap(queue)
        queue.clear()
        if (id !in out && id !in claimed) out[id] = Todo(null, null)
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
        applicationContext.sl.pending.drain(inputData.getLong(Pending.ID, 0))
        Result.success()
    }
}
