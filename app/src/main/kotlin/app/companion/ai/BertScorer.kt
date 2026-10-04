package app.companion.ai

import android.app.Application
import android.os.Process
import app.companion.core.Bert
import app.companion.core.BertPlan
import app.companion.core.Bpe
import app.companion.core.Calibration
import app.companion.core.DecideInput
import app.companion.core.Raw
import app.companion.core.Scored
import app.companion.core.Scorer
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
        }.getOrNull()
        cache = key to l
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

    private fun path(s: Spec) = File(Models.custom(app), s.file).path

    fun calibration(): Calibration = Active.bert(app)?.let { Active.calibration(app, it.spec).first } ?: Calibration.DEFAULT

    fun warm(): Boolean {
        val s = loaded()?.specs?.get(Calibration.TYPE) ?: return false
        val p = path(s)
        return try {
            low { runBlocking { gov.run(s, { DecideRunner.open(app, p) }) { true } } }
        } catch (_: Exception) {
            false
        }
    }

    private fun logits(l: Loaded, task: String, x: DecideInput): FloatArray? {
        val s = l.specs[task] ?: return null
        val p = path(s)
        val sig = l.plan.spec.signature(task, x.bucket)
        val y = if (l.plan.spec.maskFirst) DecideInput(x.mask, x.ids) else x
        return try {
            runBlocking { gov.run(s, { DecideRunner.open(app, p) }) { it.logits(y, sig) } }
        } catch (_: Exception) {
            null
        }
    }

    override fun score(raw: Raw): Scored? {
        val l = loaded() ?: return null
        val cal = Active.calibration(app, l.plan.spec).first
        val text = listOf(raw.title, raw.body).filter { it.isNotBlank() }.joinToString("\n")
        return try {
            low { Bert.score(l.plan.spec, cal, l.bpe, raw.sender.trim(), text) { task, x -> logits(l, task, x) } }
        } catch (_: Exception) {
            null
        }
    }
}
