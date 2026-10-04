package app.companion.ai

import android.app.Application
import app.companion.core.Calibration
import app.companion.core.Decide
import app.companion.core.DecideSpec
import app.companion.core.Labels
import app.companion.core.Raw
import app.companion.core.Redact
import app.companion.core.Scored
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
        return runCatching { Models.loadCalibration(app).takeIf { it.fits(Calibration.TYPE, spec.labels(Calibration.TYPE)) } }.getOrNull() ?: Calibration.DEFAULT
    }

    override fun score(raw: Raw): Scored? {
        if (!Models.installed(app, Models.decide)) return null
        val (tok, spec) = assets ?: return null
        val text = Redact.codes(raw.title + "\n" + raw.body)
        val cal = calibration()
        val path = Models.file(app, Models.decide).path
        return try {
            runBlocking {
                gov.run(Models.decide, { DecideRunner.open(app, path) }) { r ->
                    fun probs(task: String): Map<String, Float>? = runCatching {
                        val input = Decide.build(spec, task, text, tok)
                        val p = cal.probs(task, r.logits(input, spec.signature(task, input.bucket)))
                        spec.labels(task).mapIndexed { i, l -> l to p[i] }.toMap()
                    }.getOrNull()

                    val type = probs(Calibration.TYPE) ?: return@run null
                    val top = type.maxByOrNull { it.value }?.key
                    Scored(type, if (top in Labels.money) probs(Calibration.CATEGORY) else null)
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
