package app.companion.ai

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.companion.core.Fetcher
import app.companion.core.Net
import app.companion.core.Reply
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

private object Http : Net {
    private class Body(i: InputStream, private val c: HttpURLConnection) : FilterInputStream(i) {
        override fun close() {
            try {
                super.close()
            } finally {
                c.disconnect()
            }
        }
    }

    override fun get(url: String, from: Long): Reply {
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.useCaches = false
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("Accept-Encoding", "identity")
        if (from > 0) c.setRequestProperty("Range", "bytes=$from-")
        val code = c.responseCode
        val body = if (code == 200 || code == 206) c.inputStream else InputStream.nullInputStream()
        return Reply(code, c.getHeaderField("Location"), c.getHeaderField("Content-Range"), Body(body, c))
    }
}

class ModelWork(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val job = coroutineContext.job
        val fetch = Fetcher(Http)
        for (s in Manifest.all) {
            if (Models.have(applicationContext, s) != Have.No) continue
            var shown = -1
            val out = fetch.pull(Manifest.BASE + s.file, s.bytes, s.sha, Models.part(applicationContext, s), Models.base(applicationContext, s), { job.isActive }) { pos ->
                val pct = (pos * 100 / s.bytes).toInt()
                if (pct != shown) {
                    shown = pct
                    setProgressAsync(workDataOf(FILE to s.file, PCT to pct))
                }
            }
            when (out) {
                Fetcher.Out.Done -> Models.stamp(applicationContext, Models.base(applicationContext, s), s.sha)
                Fetcher.Out.Retry -> return@withContext Result.retry()
                Fetcher.Out.Bad -> return@withContext Result.failure(workDataOf(FILE to s.file))
            }
        }
        Result.success()
    }

    companion object {
        const val FILE = "file"
        const val PCT = "pct"
    }
}

object ModelJobs {
    private const val NAME = "models"

    fun start(c: Context) {
        val req = OneTimeWorkRequestBuilder<ModelWork>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(c).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, req)
    }

    fun stop(c: Context) {
        WorkManager.getInstance(c).cancelUniqueWork(NAME)
    }

    fun watch(c: Context): Flow<WorkInfo?> = WorkManager.getInstance(c).getWorkInfosForUniqueWorkFlow(NAME).map { it.firstOrNull() }
}
