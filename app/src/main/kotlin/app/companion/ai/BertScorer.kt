package app.companion.ai

import android.app.Application
import android.os.Process
import android.util.Log
import app.companion.core.Bert
import app.companion.core.BertPlan
import app.companion.core.Bpe
import app.companion.core.Calibration
import app.companion.core.DecideInput
import app.companion.core.Guard
import app.companion.core.Raw
import app.companion.core.Scored
import app.companion.core.Scorer
import app.companion.core.Weights
import java.io.File
import kotlinx.coroutines.runBlocking

class BertScorer(private val app: Application, private val gov: Governor) : Scorer {
    private class Loaded(val plan: BertPlan, val bpe: Bpe, val specs: Map<String, Spec>)

    private var cache: Pair<String, Loaded?>? = null

    @Synchronized
    private fun loaded(): Loaded? {
        val plan = Active.bert(app) ?: return null
        val key = plan.key(null)
        cache?.takeIf { it.first == key }?.let { return it.second }
        val d = Models.custom(app)
        val l = runCatching {
            val specs = listOf(Calibration.TYPE, Calibration.CATEGORY).filter(plan.spec::has).associateWith {
                val f = plan.spec.file(it)
                Spec("ModernBERT $it", f, 0, plan.shas.getValue(f), 30_000)
            }
            Loaded(plan, Bpe.fromJson(File(d, plan.spec.tokenizer).readText()), specs)
        }.onFailure { warn("load", it) }.getOrNull()
        cache = key to l
        Xnn.prune(app)
        return l
    }

    private fun <T> low(f: () -> T): T {
        val was = Process.getThreadPriority(Process.myTid())
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        try {
            return f()
        } finally {
            Process.setThreadPriority(was)
        }
    }

    private fun open(l: Loaded, task: String): () -> DecideRunner = {
        val f = l.plan.spec.file(task)
        val cache = Xnn.path(app, Weights.bert(task, l.plan.shas.getValue(f)))
        DecideRunner.open(app, File(Models.custom(app), f).path, cache, Guard.threads(vitals(app)), false)
    }

    private fun run(r: DecideRunner, l: Loaded, task: String, x: DecideInput): FloatArray {
        val sig = l.plan.spec.signature(task, x.bucket)
        return if (sig?.named == true) r.named(x, sig) else r.logits(if (l.plan.spec.maskFirst) DecideInput(x.mask, x.ids) else x, sig?.name)
    }

    fun calibration(): Calibration = Active.bert(app)?.let { Active.calibration(app, it.spec).first } ?: Calibration.DEFAULT

    fun warm(): Boolean {
        val l = loaded() ?: return false
        val s = l.specs[Calibration.TYPE] ?: return false
        return try {
            low { runBlocking { gov.run(s, open(l, Calibration.TYPE)) { true } } }
        } catch (e: Exception) {
            warn("warm", e)
            false
        }
    }

    private fun logits(l: Loaded, task: String, x: DecideInput): FloatArray? {
        val s = l.specs[task] ?: return null
        return try {
            runBlocking { gov.run(s, open(l, task)) { run(it, l, task, x) } }
        } catch (e: Exception) {
            warn(task, e)
            null
        }
    }

    private fun batch(l: Loaded, task: String, xs: List<DecideInput>): List<FloatArray?> {
        val s = l.specs[task] ?: return xs.map { null }
        return try {
            runBlocking { gov.run(s, open(l, task)) { r -> xs.map { x -> runCatching { run(r, l, task, x) }.onFailure { warn(task, it) }.getOrNull() } } }
        } catch (e: Exception) {
            warn(task, e)
            xs.map { null }
        }
    }

    private fun warn(at: String, e: Throwable) = Log.w(TAG, "$at ${e.javaClass.simpleName}: ${e.message?.take(200)}")

    private fun text(raw: Raw) = listOf(raw.title, raw.body).filter { it.isNotBlank() }.joinToString("\n")

    override fun score(raw: Raw): Scored? {
        val l = loaded() ?: return null
        val cal = Active.calibration(app, l.plan.spec).first
        return try {
            low { Bert.score(l.plan.spec, cal, l.bpe, raw.sender.trim(), text(raw)) { task, x -> logits(l, task, x) } }
        } catch (e: Exception) {
            warn("score", e)
            null
        }
    }

    override fun scoreAll(raws: List<Raw>): List<Scored?> {
        if (raws.size < 2) return raws.map(::score)
        val l = loaded() ?: return raws.map { null }
        val cal = Active.calibration(app, l.plan.spec).first
        return try {
            low { Bert.scoreAll(l.plan.spec, cal, l.bpe, raws.map { it.sender.trim() to text(it) }) { task, xs -> batch(l, task, xs) } }
        } catch (e: Exception) {
            warn("scoreAll", e)
            raws.map { null }
        }
    }

    private companion object {
        const val TAG = "Companion"
    }
}
