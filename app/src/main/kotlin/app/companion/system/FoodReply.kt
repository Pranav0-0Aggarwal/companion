package app.companion.system

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.companion.ai.vitals
import app.companion.core.Clarify
import app.companion.core.Guard
import app.companion.core.WeightNudge
import app.companion.sl
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FoodReply : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val text = RemoteInput.getResultsFromIntent(i)?.getCharSequence(FoodNotes.KEY)?.toString()?.trim()?.take(MAX).orEmpty()
        val id = i.getIntExtra(FoodNotes.ID, 0)
        if (text.isEmpty()) {
            FoodNotes.done(c, id, "Nothing to log.")
            return
        }
        val kind = i.getStringExtra(FoodNotes.KIND) ?: return
        val slot = i.getStringExtra(FoodNotes.SLOT)
        val ref = i.getLongExtra(FoodNotes.REF, 0).takeIf { it > 0 }
        val pr = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                c.sl.repo.life.queue(kind, ref, slot, text)
                FoodNotes.done(c, id, "Working on it.")
                FoodWork.kick(c)
            } finally {
                pr.finish()
            }
        }
    }

    private companion object {
        const val MAX = 300
    }
}

class FoodWork(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        val sl = c.sl
        val life = sl.repo.life
        for (r in life.replies()) {
            if (r.kind == "weight") {
                val kg = WeightNudge.parseKg(r.text)
                if (kg == null) FoodNotes.done(c, 300, "Send your weight in kg, like 72.4.") else {
                    val now = System.currentTimeMillis()
                    life.weigh(LocalDate.now(ZoneId.systemDefault()), kg, now)
                    Health.weight(c, kg, now)
                    FoodNotes.done(c, 300, "Saved $kg kg.")
                }
                life.done(r.id)
                continue
            }
            val ready = sl.chat.ready()
            if (ready && Guard.hold(vitals(c)) != null) return Result.retry()
            val foods = sl.chat.foods
            val slot = r.slot?.lowercase()
            val said = if (ready) {
                val ask = when {
                    foods.pending -> r.text
                    r.kind == "order" -> "I ordered: ${r.text}"
                    else -> "I had this${slot?.let { " for $it" }.orEmpty()}: ${r.text}"
                }
                sl.chat.reply(ask, r.ref, true).let { it.ask ?: it.text.ifBlank { null } }
            } else {
                val w = foods.wen(slot.orEmpty())
                foods.quiet(Clarify.spoken(r.text), w, if (r.ref != null) "order" else "chat", null, r.ref)
            }
            FoodNotes.done(c, if (r.ref != null) FoodNotes.orderId(r.ref) else FoodNotes.slotId(r.slot), said ?: "I couldn't log that. Open Companion to try again.")
            life.done(r.id)
        }
        return Result.success()
    }

    companion object {
        fun kick(c: Context) {
            val req = OneTimeWorkRequestBuilder<FoodWork>().setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
            WorkManager.getInstance(c).enqueueUniqueWork("foodreply", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }
    }
}
