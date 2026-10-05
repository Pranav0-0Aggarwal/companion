package app.companion.data

import android.content.Context
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.companion.ai.Pending
import app.companion.core.Calibration
import app.companion.core.Fingerprint
import app.companion.core.Flow
import app.companion.core.Flows
import app.companion.core.Guard
import app.companion.core.Refile
import app.companion.core.Rules
import app.companion.core.Source
import app.companion.core.Template
import app.companion.core.text
import app.companion.ai.vitals
import app.companion.sl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class Upgrade(private val c: Context, private val db: Db) {
    private val d = db.dao()

    suspend fun run(): Boolean {
        val m = d.mark(JOB)
        val cap = m?.cap ?: d.top()
        var pos = m?.pos ?: 0L
        if (m != null && pos >= cap) return true
        val own = Flows.own(d.ownLast4() + d.cardLast4())
        val moved = d.movedNow().toSet()
        while (true) {
            currentCoroutineContext().ensureActive()
            if (Guard.hold(vitals(c)) != null) return false
            val rows = d.page(pos, cap, PAGE)
            if (rows.isEmpty()) break
            val from = d.senders(rows.map { it.id }).distinctBy { it.itemId }.associate { it.itemId to it.sender }
            val raws = rows.associate { i -> i.id to Pending.raw(c, i, from[i.id])?.takeIf { it.source.name in Refile.SOURCES_SET } }
            db.withTransaction {
                val keys = HashMap<String, HashMap<String, Int>>()
                rows.forEach { i ->
                    val raw = raws[i.id]
                    val tpl = raw?.let(Template::of)
                    val e = i.move()
                    val flow = e?.let { v ->
                        if (v.merchant?.let { Fingerprint.norm(it) } in moved) Flow.Self.name else Flows.of(v, raw?.text() ?: "${i.title} ${i.note}", own)?.name
                    }
                    if ((tpl != null && tpl != i.tpl) || flow != i.flow) d.retag(i.id, tpl, flow)
                    if (tpl != null && i.tpl != null && tpl != i.tpl) keys.getOrPut(i.tpl) { HashMap() }.merge(tpl, 1, Int::plus)
                }
                keys.forEach { (old, news) -> rekey(old, news.maxBy { it.value }.key, rows.any { it.tpl == old && it.merchant != null }) }
                pos = rows.last().id
                d.putMark(Mark(JOB, pos, cap, 0, 0, 0, 0, 0, null, false, false))
            }
        }
        d.putMark(Mark(JOB, cap, cap, 0, 0, 0, 0, 0, null, false, false))
        return true
    }

    private suspend fun rekey(old: String, new: String, merchant: Boolean) {
        d.rulesFor(old).filter { !(merchant && it.task == Calibration.CATEGORY) }.forEach { r ->
            val cur = d.rule(new, r.task)
            val (label, count) = cur?.let { Rules.merge(it.label to it.count, r.label to r.count) } ?: (r.label to r.count)
            d.putRule(TemplateRule(new, r.task, label, count))
        }
        d.dropRules(old)
    }

    companion object {
        private const val JOB = "upgrade6"
        private const val PAGE = 200

        fun boot(c: Context) {
            val req = OneTimeWorkRequestBuilder<UpgradeWork>().setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build()).build()
            WorkManager.getInstance(c).enqueueUniqueWork(JOB, ExistingWorkPolicy.KEEP, req)
        }
    }
}

class UpgradeWork(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (Upgrade(applicationContext, applicationContext.sl.db).run()) Result.success() else Result.retry()
    }
}
