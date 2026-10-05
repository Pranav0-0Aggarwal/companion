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
import app.companion.core.Category
import app.companion.core.Fingerprint
import app.companion.core.Flow
import app.companion.core.Flows
import app.companion.core.Guard
import app.companion.core.Merchant
import app.companion.core.Refile
import app.companion.core.Rules
import app.companion.core.Senders
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

    suspend fun run() = tpl() && names() && tidy()

    private suspend fun tpl(): Boolean {
        val m = d.mark(TPL)
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
            val raws = rows.associate { i -> i.id to Pending.raw(c, i, from[i.id])?.takeIf { it.source == Source.Sms } }
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
                d.putMark(Mark(TPL, pos, cap, 0, 0, 0, 0, 0, null, false, false))
            }
        }
        d.putMark(Mark(TPL, cap, cap, 0, 0, 0, 0, 0, null, false, false))
        return true
    }

    private suspend fun names(): Boolean {
        val m = d.mark(JOB)
        val cap = m?.cap ?: d.top()
        var pos = m?.pos ?: 0L
        if (m != null && pos >= cap) return true
        val taught = d.allRules().map { it.hash }.toSet()
        while (true) {
            currentCoroutineContext().ensureActive()
            if (Guard.hold(vitals(c)) != null) return false
            val rows = d.page(pos, cap, PAGE)
            if (rows.isEmpty()) break
            val held = d.aliasNames().toSet()
            val ruled = rows.filter { it.tpl in taught }
            val bare = rows.filter { it.kind == "Bill" && it.merchant == null }
            val fixed = d.corrected(rows.map { it.id }).toSet()
            val from = (ruled + bare).let { l -> if (l.isEmpty()) emptyMap() else d.senders(l.map { it.id }).distinctBy { it.itemId }.associate { it.itemId to it.sender } }
            val raws = ruled.associate { i -> i.id to Pending.raw(c, i, from[i.id])?.takeIf { it.source == Source.Sms } }
            db.withTransaction {
                val keys = HashMap<String, HashMap<String, Int>>()
                rows.forEach { i ->
                    val old = i.merchant
                    if (old == null && i.kind == "Bill") {
                        from[i.id]?.let(Merchant::fromSender)?.let { n -> d.alias(Merchant.key(n)) ?: n }?.let { to -> d.setNamed(i.id, to, if (i.title == "Bill") "$to bill" else i.title) }
                    }
                    if (i.kind == "Credit" && i.flow == Flow.CardBill.name && (i.state == State.ASK || i.state == State.CHECK)) d.setState(i.id, State.SETTLED)
                    if (old != null && i.kind in NAMED && old !in held && i.id !in fixed) {
                        val clean = Merchant.resolve(old)
                        val to = clean?.let { n -> d.alias(Merchant.key(n)) ?: n }
                        if (to != null && to != old) {
                            d.setNamed(i.id, to, if (i.title.startsWith(old)) to + i.title.removePrefix(old) else i.title)
                            val (a, b) = Fingerprint.norm(old).orEmpty() to Fingerprint.norm(to).orEmpty()
                            if (a != b) {
                                d.rekeyLearned(a, b)
                                d.rekeyMoved(a, b)
                            }
                        }
                    }
                    val tpl = raws[i.id]?.let(Template::of)
                    if (tpl != null && tpl != i.tpl) {
                        d.retag(i.id, tpl, i.flow)
                        i.tpl?.let { keys.getOrPut(it) { HashMap() }.merge(tpl, 1, Int::plus) }
                    }
                }
                keys.forEach { (old, news) -> rekey(old, news.maxBy { it.value }.key, rows.any { it.tpl == old && it.merchant != null }) }
                pos = rows.last().id
                d.putMark(Mark(JOB, pos, cap, 0, 0, 0, 0, 0, null, false, false))
            }
        }
        d.putMark(Mark(JOB, cap, cap, 0, 0, 0, 0, 0, null, false, false))
        return true
    }

    private suspend fun tidy(): Boolean {
        val m = d.mark(V8)
        val cap = m?.cap ?: d.top()
        var pos = m?.pos ?: 0L
        if (m != null && pos >= cap) return true
        while (true) {
            currentCoroutineContext().ensureActive()
            val rows = d.page(pos, cap, PAGE)
            if (rows.isEmpty()) break
            val titled = rows.filter { it.src == "Sms" && it.kind in headed }
            val from = if (titled.isEmpty()) emptyMap() else d.senders(titled.map { it.id }).distinctBy { it.itemId }.associate { it.itemId to it.sender }
            val named = rows.filter { it.money && it.merchant != null && (it.category == Category.Transfer.label || it.category == Category.Other.label) }
            val corrected = d.corrected(named.map { it.id }).toSet()
            val taught = d.taught(named.mapNotNull { it.tpl }).toSet()
            db.withTransaction {
                titled.forEach { i ->
                    val s = from[i.id]
                    if (s != null && i.title == s) Senders.title(s).takeIf { it != s }?.let { d.setTitle(i.id, it) }
                }
                named.forEach { i ->
                    val cat = Category.of(i.merchant, i.credit)
                    val key = Fingerprint.norm(i.merchant)
                    if (cat != Category.Other && cat.label != i.category && (i.credit || cat != Category.Income) && key != null && d.learned(key) == null && !Refile.locked(i.filed(), true, i.id in corrected, i.tpl in taught)) {
                        d.recat(i.id, cat.label, if (i.state == State.CHECK) State.SETTLED else i.state)
                    }
                }
                pos = rows.last().id
                d.putMark(Mark(V8, pos, cap, 0, 0, 0, 0, 0, null, false, false))
            }
        }
        db.withTransaction {
            d.settle(System.currentTimeMillis())
            d.putMark(Mark(V8, cap, cap, 0, 0, 0, 0, 0, null, false, false))
        }
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
        private const val TPL = "upgrade6"
        private const val JOB = "upgrade7b"
        private const val V8 = "upgrade8"
        private val NAMED = setOf("Debit", "Credit", "CardSpend", "Bill", "Delivery")
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
