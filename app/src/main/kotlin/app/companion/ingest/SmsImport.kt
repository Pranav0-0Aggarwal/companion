package app.companion.ingest

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.provider.Telephony
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.companion.core.Raw
import app.companion.core.Source
import app.companion.sl
import app.companion.system.Live
import app.companion.ui.has

object SmsImport {
    fun enqueue(c: Context) {
        val work = OneTimeWorkRequestBuilder<SmsImportWorker>()
            .setConstraints(Constraints.Builder().setRequiresCharging(true).setRequiresDeviceIdle(true).build())
            .build()
        WorkManager.getInstance(c).enqueueUniqueWork("sms-import", ExistingWorkPolicy.KEEP, work)
    }
}

class SmsImportWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        val sl = applicationContext.sl
        if (sl.repo.profileNow().imported || !applicationContext.has(Manifest.permission.READ_SMS)) return Result.success()
        var offset = 0
        do {
            val n = page(offset)
            offset += n
        } while (n == PAGE)
        sl.repo.edit { it.copy(imported = true) }
        Live.refresh(applicationContext)
        return Result.success()
    }

    private suspend fun page(offset: Int): Int {
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${Telephony.Sms.DATE} DESC")
            putInt(ContentResolver.QUERY_ARG_LIMIT, PAGE)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
        }
        val cols = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        val ingest = applicationContext.sl.ingest
        var n = 0
        applicationContext.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, cols, args, null)?.use { c ->
            while (c.moveToNext()) {
                ingest.handle(Raw(Source.Sms, c.getString(0).orEmpty(), "", c.getString(1).orEmpty(), c.getLong(2)), refresh = false)
                n++
            }
        }
        return n
    }

    private companion object {
        const val PAGE = 200
    }
}
