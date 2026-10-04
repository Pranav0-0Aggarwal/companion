package app.companion.ai

import android.app.Application
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.companion.core.Batch
import app.companion.core.Guard
import app.companion.core.Progress
import app.companion.data.mark
import app.companion.ingest.SmsImport
import app.companion.sl
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class Job { Import, Reprocess }

data class Run(val job: Job, val done: Int, val total: Int, val etaSec: Long?, val paused: Boolean, val reason: String?)

object Processing {
    internal const val NAME = "process"
    internal const val JOB = "job"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Mutex()
    private val run = MutableStateFlow<Run?>(null)
    private val changed = MutableStateFlow(false)
    internal val token = AtomicLong()

    val state: StateFlow<Run?> = run.asStateFlow()
    val modelChanged: StateFlow<Boolean> = changed.asStateFlow()

    fun start(c: Context, job: Job, battery: Boolean) {
        val app = c.applicationContext
        scope.launch { begin(app, job, battery) }
    }

    fun pause(c: Context) {
        val app = c.applicationContext
        scope.launch { halt(app) }
    }

    fun resume(c: Context) {
        val app = c.applicationContext
        scope.launch { again(app) }
    }

    fun cancel(c: Context) {
        val app = c.applicationContext
        scope.launch { end(app) }
    }

    suspend fun count(c: Context, job: Job): Int = withContext(Dispatchers.IO) {
        val app = c.applicationContext
        verify(app)
        Batch.left(plan(app, job))
    }

    internal fun mine(t: Long) = token.get() == t

    internal fun publish(r: Run?, t: Long) {
        if (mine(t)) run.value = r
    }

    internal suspend fun begin(c: Context, job: Job, battery: Boolean) = gate.withLock { go(c, job, battery) }

    internal suspend fun again(c: Context) = gate.withLock {
        val repo = c.sl.repo
        Job.entries.firstNotNullOfOrNull { j -> repo.mark(j.name)?.takeIf { it.held } }?.let { go(c, Job.valueOf(it.job), it.battery) }
    }

    internal suspend fun halt(c: Context) = gate.withLock {
        val r = run.value ?: return@withLock
        val repo = c.sl.repo
        token.incrementAndGet()
        WorkManager.getInstance(c).cancelUniqueWork(NAME)
        repo.setPaused(r.job.name, true)
        if (repo.mark(r.job.name) == null) {
            run.value = null
            return@withLock
        }
        val paused = r.copy(etaSec = null, paused = true, reason = null)
        run.value = paused
        Note.paused(c, paused)
    }

    internal suspend fun end(c: Context) = gate.withLock {
        token.incrementAndGet()
        WorkManager.getInstance(c).cancelUniqueWork(NAME)
        Job.entries.forEach { c.sl.repo.setPaused(it.name, false) }
        run.value = null
        Note.clear(c)
    }

    internal suspend fun park(c: Context, job: Job, p: Progress, battery: Boolean, why: String, t: Long) = gate.withLock {
        if (!mine(t)) return@withLock
        val repo = c.sl.repo
        if (repo.mark(job.name) == null) return@withLock
        val r = Run(job, p.done, p.total, null, true, why)
        repo.setWhy(job.name, why)
        run.value = r
        WorkManager.getInstance(c).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request(job, battery, why))
        Note.paused(c, r)
    }

    internal suspend fun settle(c: Context, job: Job) = withContext(NonCancellable) {
        gate.withLock { if (run.value?.job == job && c.sl.repo.mark(job.name) == null) run.value = null }
    }

    internal fun idle(job: Job, p: Progress, battery: Boolean) = Run(job, p.done, p.total, null, !battery, Guard.waiting(battery))

    private fun request(job: Job, battery: Boolean, why: String?): OneTimeWorkRequest {
        val w = Guard.wait(why, battery)
        val b = OneTimeWorkRequestBuilder<ProcessWork>().setInputData(workDataOf(JOB to job.name)).addTag(job.name)
        if (w.free) return b.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
        val cons = Constraints.Builder().setRequiresCharging(w.charging).setRequiresDeviceIdle(w.idle).setRequiresBatteryNotLow(w.notLow).build()
        return b.setInitialDelay(w.delay, TimeUnit.MILLISECONDS).setConstraints(cons).build()
    }

    internal suspend fun verify(c: Context) {
        val sha = Active.sha(c)
        changed.value = sha != null && sha != c.sl.repo.profileNow().model
    }

    internal fun boot(app: Application) {
        scope.launch {
            launch {
                combine(app.sl.repo.profile.map { it.model }.distinctUntilChanged(), Dl.state.map { it.mode }.distinctUntilChanged()) { _, _ -> }.collect { verify(app) }
            }
            restore(app)
        }
    }

    private suspend fun go(c: Context, job: Job, battery: Boolean) {
        if (run.value?.paused == false) return
        val repo = c.sl.repo
        val p = plan(c, job)
        if (Batch.left(p) == 0 && !(job == Job.Import && mail(c))) {
            if (job == Job.Reprocess && p.sha != null) {
                repo.atomic {
                    repo.edit { it.copy(model = p.sha) }
                    repo.dropMark(job.name)
                }
                verify(c)
            }
            return
        }
        token.incrementAndGet()
        repo.putMark(p.mark(job.name, battery))
        run.value = Run(job, p.done, p.total, null, !battery, if (battery) null else Guard.CHARGER)
        Note.clear(c)
        WorkManager.getInstance(c).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request(job, battery, null))
    }

    private suspend fun mail(c: Context) = c.sl.gmail.enabled && c.sl.repo.profileNow().mail

    private suspend fun plan(c: Context, job: Job): Progress {
        val repo = c.sl.repo
        val sha = if (job == Job.Reprocess) Active.sha(c) else null
        val p = Batch.resume(repo.mark(job.name)?.progress(), sha)
            ?: if (job == Job.Reprocess) Batch.start(0, repo.top(), sha) else Batch.start(Long.MAX_VALUE, repo.topLink(), null)
        val left = when {
            job == Job.Reprocess -> if (sha == null) 0 else repo.stale(p.pos, p.cap)
            repo.profileNow().let { it.imported || !it.sms } -> 0
            else -> SmsImport.count(c, p.pos)
        }
        return Batch.fit(p, left)
    }

    private suspend fun restore(c: Context) {
        val repo = c.sl.repo
        val infos = withContext(Dispatchers.IO) { WorkManager.getInstance(c).getWorkInfosForUniqueWork(NAME).get() }
        val live = infos.firstOrNull { !it.state.isFinished }?.tags?.firstNotNullOfOrNull { t -> Job.entries.firstOrNull { it.name == t } }
        val m = live?.let { repo.mark(it.name) } ?: Job.entries.firstNotNullOfOrNull { j -> repo.mark(j.name)?.takeIf { it.held } } ?: return
        val job = Job.valueOf(m.job)
        if (run.value == null) run.value = if (m.held) Run(job, m.done, m.total, null, true, m.why) else idle(job, m.progress(), m.battery)
    }
}
