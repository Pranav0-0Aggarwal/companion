package app.companion.system

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.companion.sl

class Sweep(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        applicationContext.sl.repo.retain()
        Live.refresh(applicationContext)
        return Result.success()
    }
}
