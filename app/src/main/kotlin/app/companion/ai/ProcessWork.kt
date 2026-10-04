package app.companion.ai

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import app.companion.core.Batch
import app.companion.core.Guard
import app.companion.core.Pace
import app.companion.core.Progress
import app.companion.core.Raw
import app.companion.core.Refile
import app.companion.core.Source
import app.companion.core.Verdict
import app.companion.core.Vitals
import app.companion.data.Item
import app.companion.data.State
import app.companion.ingest.SmsImport
import app.companion.sl
import app.companion.system.Live
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class ProcessWork(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun getForegroundInfo() = Note.info(applicationContext, Processing.state.value)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val job = Job.entries.firstOrNull { it.name == inputData.getString(Processing.JOB) } ?: return@withContext Result.failure()
        Engine(applicationContext, job, Processing.token.get()) {
            try {
                setForeground(it)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }.go()
    }
}

private fun vitals(c: Context): Vitals {
    val pm = c.getSystemService(PowerManager::class.java)
    val b = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val plugged = (b?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    return Vitals(pm.currentThermalStatus, pm.isPowerSaveMode, plugged, if (level < 0 || scale <= 0) 100 else level * 100 / scale)
}

private class Hold(val why: String) : Exception()

private class Engine(private val c: Context, private val job: Job, private val token: Long, private val fore: suspend (ForegroundInfo) -> Unit) {
    private val sl = c.sl
    private val repo = sl.repo
    private val pace = Pace()
    private lateinit var cur: Progress
    private var battery = false

    suspend fun go(): Result {
        val m = repo.mark(job.name)
        if (m == null || m.paused) return Result.success()
        cur = m.progress()
        battery = m.battery
        try {
            Guard.hold(vitals(c))?.let { return park(it) }
            Note.resumed(c)
            show()
            val ready = sl.gov.hold {
                if (job == Job.Reprocess && !sl.scorer.warm()) return@hold false
                if (job == Job.Reprocess) reprocess() else import()
                true
            }
            if (!ready) return halted()
            finish()
            return Result.success()
        } catch (h: Hold) {
            return park(h.why)
        } catch (e: CancellationException) {
            Processing.publish(Processing.idle(job, cur, battery), token)
            throw e
        } catch (_: Exception) {
            Processing.publish(null, token)
            return Result.failure()
        }
    }

    private suspend fun park(why: String): Result {
        Processing.park(c, job, cur, battery, why, token)
        return Result.success()
    }

    private suspend fun halted(): Result {
        val why = if (Models.file(c, Manifest.decide) == null) "No model installed" else "Not enough free memory, try again"
        Processing.publish(null, token)
        Note.done(c, job, why)
        return Result.failure()
    }

    private suspend fun show() {
        if (!Processing.mine(token)) return
        val r = Run(job, cur.done, cur.total, pace.eta(Batch.left(cur)), false, null)
        Processing.publish(r, token)
        fore(Note.info(c, r))
    }

    private suspend fun gate(): Long {
        val v = vitals(c)
        Guard.hold(v)?.let { throw Hold(it) }
        val t = SystemClock.elapsedRealtime()
        Guard.nap(v).takeIf { it > 0 }?.let { delay(it) }
        return t
    }

    private suspend fun lap(t: Long, n: Int) {
        pace.add(n, SystemClock.elapsedRealtime() - t)
        show()
    }

    private suspend fun reprocess() {
        while (Batch.left(cur) > 0) {
            val t = gate()
            val rows = repo.stale(cur.pos, cur.cap, Batch.size(Batch.left(cur)))
            if (rows.isEmpty()) break
            val frozen = repo.frozen(rows)
            val from = repo.senders(rows.map { it.id })
            val p = repo.profileNow()
            val out = rows.filter { it.id !in frozen }.associate { it.id to read(it, from[it.id]) }
            cur = repo.atomic {
                var moved = 0
                var ask = 0
                for ((id, r) in out) {
                    if (r == null) continue
                    val ch = repo.refile(id, r.first, r.second, p)
                    if (ch.moved) moved++
                    if (ch.ask) ask++
                }
                Batch.step(cur, rows.last().id, rows.size, moved, ask, out.count { it.value == null }).also { repo.step(job.name, it) }
            }
            lap(t, rows.size)
        }
    }

    private suspend fun read(i: Item, from: String?): Pair<Raw, Verdict>? {
        currentCoroutineContext().ensureActive()
        val raw = Pending.raw(c, i, from) ?: return null
        val v = sl.refine.run(raw)
        return if (Refile.usable(v)) raw to v else null
    }

    private suspend fun import() {
        val p0 = repo.profileNow()
        if (!p0.imported && p0.sms && SmsImport.can(c)) {
            sl.scorer.warm()
            while (Batch.left(cur) > 0) {
                val t = gate()
                val rows = SmsImport.page(c, cur.pos, Batch.size(Batch.left(cur)))
                if (rows.isEmpty()) break
                val p = repo.profileNow()
                val out = rows.filterNot { repo.seen(it.from, it.sent, it.at, cur.cap) }.map {
                    currentCoroutineContext().ensureActive()
                    Raw(Source.Sms, it.from, "", it.body, it.at).let { r -> r to sl.refine.run(r) }
                }
                cur = repo.atomic {
                    var added = 0
                    var ask = 0
                    for ((r, v) in out) {
                        val a = repo.add(r, v, p)?.takeIf { it.fresh } ?: continue
                        added++
                        if (a.item.state == State.ASK) ask++
                    }
                    Batch.step(cur, rows.last().id, rows.size, added, ask).also { repo.step(job.name, it) }
                }
                lap(t, rows.size)
            }
            repo.atomic {
                repo.edit { it.copy(imported = true) }
                repo.unfold()
            }
        }
        val p = repo.profileNow()
        if (p.mail && sl.gmail.enabled) {
            gate()
            val n = soft { sl.gmail.token(c)?.let { sl.gmail.sync(it) } } ?: 0
            cur = cur.copy(done = cur.done + n, total = cur.total + n)
            repo.step(job.name, cur)
        }
    }

    private suspend fun <T> soft(f: suspend () -> T): T? = try {
        f()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun finish() {
        val text = if (job == Job.Reprocess) Refile.summary(cur.moved, cur.ask, cur.skip) else Refile.imported(cur.done, cur.moved, cur.ask)
        val sha = cur.sha ?: Models.sha(c, Manifest.decide)
        repo.atomic {
            repo.edit { if (job == Job.Reprocess || it.model == null) it.copy(model = sha) else it }
            repo.dropMark(job.name)
        }
        Live.refresh(c)
        Processing.settle(c, job)
        Note.done(c, job, text)
        Processing.verify(c)
    }
}
