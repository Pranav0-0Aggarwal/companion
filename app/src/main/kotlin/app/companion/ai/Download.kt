package app.companion.ai

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.companion.core.Book
import app.companion.core.Fetcher
import app.companion.core.Net
import app.companion.core.Rate
import app.companion.core.Reply
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

private object Http : Net {
    private class Body(i: InputStream, private val c: HttpURLConnection) : FilterInputStream(i) {
        private var eof = false

        override fun read(): Int = super.read().also { if (it < 0) eof = true }

        override fun read(b: ByteArray, o: Int, l: Int): Int = super.read(b, o, l).also { if (it < 0) eof = true }

        override fun close() {
            try {
                super.close()
            } finally {
                if (!eof) c.disconnect()
            }
        }
    }

    override fun get(url: String, from: Long, to: Long): Reply {
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.useCaches = false
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("Accept-Encoding", "identity")
        if (from > 0 || to >= 0) c.setRequestProperty("Range", "bytes=$from-${if (to >= 0) to else ""}")
        val code = c.responseCode
        val body = if (code == 200 || code == 206) c.inputStream else InputStream.nullInputStream()
        return Reply(code, c.getHeaderField("Location"), c.getHeaderField("Content-Range"), Body(body, c))
    }
}

enum class Mode { Idle, Waiting, Running, Paused, Failed, Full }

enum class Pull { Done, Retry, Stop, Bad, Full }

data class DlState(
    val mode: Mode = Mode.Idle,
    val cur: String? = null,
    val pos: Long = 0,
    val ready: Long = 0,
    val speed: Long = 0,
    val eta: Long = -1,
    val need: Long = 0,
)

object Dl {
    val state = MutableStateFlow(DlState())

    @Volatile
    var paused = false

    private val gate = ReentrantLock()
    private const val TRIES = 3

    private val pins get() = Manifest.all.filter(Models::pinned)

    val total get() = pins.sumOf { it.bytes }

    private fun prefs(c: Context) = c.getSharedPreferences("models", Context.MODE_PRIVATE)

    fun want(c: Context, on: Boolean) = prefs(c).edit().putBoolean("want", on).apply()

    fun wants(c: Context) = prefs(c).getBoolean("want", false)

    fun partial(c: Context, s: Spec): Long {
        val p = Models.part(c, s)
        val b = Book(File(p.path + ".ranges"), s.bytes, s.sha)
        return if (b.known()) b.done() else p.length().takeIf { it < s.bytes } ?: 0
    }

    fun sync(c: Context) {
        if (state.value.mode == Mode.Idle && ModelJobs.pending(c)) state.value = DlState(Mode.Waiting)
    }

    fun wipe(c: Context) = gate.withLock {
        Manifest.all.forEach { s ->
            val p = Models.part(c, s)
            listOf(p, File(p.path + ".ranges"), File(p.path + ".ranges.tmp")).forEach(File::delete)
        }
        want(c, false)
        paused = false
        state.value = DlState()
    }

    fun run(c: Context, live: () -> Boolean, ping: (DlState) -> Unit = {}): Pull {
        if (!gate.tryLock(15, TimeUnit.SECONDS)) return Pull.Retry
        try {
            return go(c, { live() && !paused }, ping)
        } finally {
            gate.unlock()
        }
    }

    private fun go(c: Context, alive: () -> Boolean, ping: (DlState) -> Unit): Pull {
        val todo = pins.filter { Models.have(c, it) == Have.No }
        var ready = total - todo.sumOf { it.bytes }
        val rate = Rate()
        val at = AtomicLong()
        val fetch = Fetcher(Http)
        fun put(s: DlState) {
            state.value = s
            ping(s)
        }
        rate.add(SystemClock.elapsedRealtime(), ready)
        put(DlState(Mode.Running, ready = ready))
        for (s in todo) {
            var tries = 0
            while (true) {
                val out = fetch.pull(Manifest.url(s), s.bytes, s.sha, Models.part(c, s), Models.base(c, s), alive) { pos ->
                    val t = SystemClock.elapsedRealtime()
                    val p = at.get()
                    if (alive() && (pos >= s.bytes || t - p >= 250) && at.compareAndSet(p, t)) {
                        rate.add(t, ready + pos)
                        put(DlState(Mode.Running, s.file, pos, ready, rate.bps(), rate.eta(total - ready - pos)))
                    }
                }
                when (out) {
                    Fetcher.Out.Done -> {
                        Models.stamp(c, Models.base(c, s), s.sha)
                        ready += s.bytes
                        break
                    }
                    Fetcher.Out.Retry -> {
                        if (!alive()) return end(c, Pull.Stop)
                        if (++tries > TRIES) return end(c, Pull.Retry)
                        repeat(tries * 10) { if (alive()) Thread.sleep(100) }
                    }
                    Fetcher.Out.Bad -> return end(c, Pull.Bad)
                    Fetcher.Out.Full -> return end(c, Pull.Full, todo.dropWhile { it !== s }.sumOf { it.bytes - partial(c, it) })
                }
            }
        }
        return end(c, Pull.Done)
    }

    private fun end(c: Context, r: Pull, need: Long = 0): Pull {
        if (r != Pull.Retry && r != Pull.Stop) want(c, false)
        state.value = when (r) {
            Pull.Done -> DlState()
            Pull.Retry -> DlState(Mode.Waiting)
            Pull.Stop -> DlState(if (paused) Mode.Paused else Mode.Waiting)
            Pull.Bad -> DlState(Mode.Failed)
            Pull.Full -> DlState(Mode.Full, need = need)
        }
        return r
    }
}

class ModelWork(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val job = coroutineContext.job
        when (Dl.run(applicationContext, { job.isActive })) {
            Pull.Done -> Result.success()
            Pull.Bad, Pull.Full -> Result.failure()
            Pull.Retry, Pull.Stop -> Result.retry()
        }
    }
}

object ModelJobs {
    const val ID = 4107
    private const val FALLBACK = "models"

    private fun js(c: Context) = c.getSystemService(JobScheduler::class.java)

    fun pending(c: Context) = js(c).getPendingJob(ID) != null

    fun start(c: Context) {
        Dl.paused = false
        Dl.want(c, true)
        drop(c)
        Dl.state.value = DlState(Mode.Waiting)
        val job = JobInfo.Builder(ID, ComponentName(c, ModelJob::class.java))
            .setUserInitiated(true)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
            .setEstimatedNetworkBytes(Dl.total, JobInfo.NETWORK_BYTES_UNKNOWN.toLong())
            .build()
        if (js(c).schedule(job) != JobScheduler.RESULT_SUCCESS) fallback(c)
    }

    fun pause(c: Context) {
        Dl.paused = true
        Dl.want(c, false)
        Dl.state.value = Dl.state.value.copy(mode = Mode.Paused, speed = 0, eta = -1)
        js(c).cancel(ID)
        drop(c)
    }

    fun cancel(c: Context) {
        Dl.paused = true
        js(c).cancel(ID)
        drop(c)
        Dl.wipe(c)
    }

    fun kill(c: Context) {
        Dl.paused = true
        Dl.want(c, false)
        js(c).cancel(ID)
    }

    fun fallback(c: Context) {
        val req = OneTimeWorkRequestBuilder<ModelWork>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(c).enqueueUniqueWork(FALLBACK, ExistingWorkPolicy.KEEP, req)
    }

    fun drop(c: Context) {
        WorkManager.getInstance(c).cancelUniqueWork(FALLBACK)
    }
}
