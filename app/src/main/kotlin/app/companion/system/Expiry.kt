package app.companion.system

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.companion.core.DocKind
import app.companion.core.Expiry
import app.companion.core.Face
import app.companion.core.Suggestion
import app.companion.sl
import app.companion.ui.has
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

object VaultPrefs {
    private fun p(c: Context) = c.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)

    fun cal(c: Context) = p(c).getBoolean("cal", false)

    fun cal(c: Context, on: Boolean) = p(c).edit().putBoolean("cal", on).apply()

    fun mirror(c: Context, cal: Long?, kind: DocKind, title: String, expires: Long) {
        if (!cal(c) || !c.has(Manifest.permission.WRITE_CALENDAR)) return
        Cals.add(c, cal, Suggestion.Cal("${kind.label} expires: $title", expires, expires + 86_400_000L, true, ""))
    }
}

object ExpiryNotes {
    const val CHAN = "vault"
    private const val TAG = "vault"

    fun post(c: Context, id: Int, kind: DocKind, title: String, days: Int) {
        if (!c.has(Manifest.permission.POST_NOTIFICATIONS)) return
        val n = Ping.base(c, CHAN, Face("Document reminder", Expiry.text(kind, title, days)), Face("Document reminder"), Prefs.get(c))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(Notes.to(c, "today"))
            .build()
        c.getSystemService(NotificationManager::class.java).notify(TAG, id, n)
    }
}

class ExpiryWork(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        val docs = c.sl.repo.docs
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate()
        for (r in docs.expiring()) {
            val on = Instant.ofEpochMilli(r.expires ?: continue).atZone(zone).toLocalDate()
            val m = Expiry.due(on, today, Expiry.parseSent(r.sent.orEmpty())) ?: continue
            ExpiryNotes.post(c, r.id.toInt(), DocKind.entries.firstOrNull { it.name == r.kind } ?: DocKind.Other, r.title, ChronoUnit.DAYS.between(today, on).toInt())
            docs.sent(r, m)
        }
        return Result.success()
    }

    companion object {
        fun boot(c: Context) {
            val req = PeriodicWorkRequestBuilder<ExpiryWork>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(c).enqueueUniquePeriodicWork("expiry", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
