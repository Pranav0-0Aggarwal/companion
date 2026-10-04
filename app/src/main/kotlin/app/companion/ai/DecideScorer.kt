package app.companion.ai

import android.app.Application
import app.companion.core.Calibration
import app.companion.core.Decide
import app.companion.core.DecideSpec
import app.companion.core.Kind
import app.companion.core.Raw
import app.companion.core.Redact
import app.companion.core.Scorer
import app.companion.core.SpTokenizer
import kotlinx.coroutines.runBlocking

class DecideScorer(private val app: Application, private val gov: Governor) : Scorer {
    private val assets by lazy {
        runCatching {
            SpTokenizer.fromJson(Models.asset(app, Models.TOKENIZER).readText()) to DecideSpec.fromJson(Models.asset(app, Models.SCHEMA).readText())
        }.getOrNull()
    }

    fun calibration(): Calibration {
        val spec = assets?.second ?: return Calibration.DEFAULT
        return runCatching { Models.loadCalibration(app).takeIf { it.fits(TASK, spec.labels(TASK)) } }.getOrNull() ?: Calibration.DEFAULT
    }

    override fun score(raw: Raw): Map<Kind, Float>? {
        if (!Models.installed(app, Models.decide)) return null
        val (tok, spec) = assets ?: return null
        val input = Decide.build(spec, TASK, Redact.codes(raw.title + "\n" + raw.body), tok)
        val path = Models.file(app, Models.decide).path
        val logits = try {
            runBlocking { gov.run(Models.decide, { DecideRunner.open(app, path) }) { it.logits(input) } }
        } catch (_: Exception) {
            return null
        }
        val p = calibration().probs(TASK, logits)
        val labels = spec.labels(TASK)
        return labels.indices.mapNotNull { i -> Kind.entries.firstOrNull { it.name.equals(labels[i], true) }?.let { it to p[i] } }.toMap()
    }

    private companion object {
        const val TASK = Calibration.TYPE
    }
}
