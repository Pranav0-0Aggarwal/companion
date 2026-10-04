package app.companion.system

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class Sweep(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        Live.refresh(applicationContext)
        return Result.success()
    }
}
