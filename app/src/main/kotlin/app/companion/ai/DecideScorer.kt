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
    private var cache: Pair<String, Pair<SpTokenizer, DecideSpec>>? = null

    @Synchronized
    private fun assets(): Pair<SpTokenizer, DecideSpec>? {
        val t = Models.file(app, Manifest.tokenizer) ?: return null
        val s = Models.file(app, Manifest.schema) ?: return null
        val key = "${t.path}:${t.lastModified()}:${s.path}:${s.lastModified()}"
        cache?.takeIf { it.first == key }?.let { return it.second }
        return runCatching { SpTokenizer.fromDtk(t.readBytes()) to DecideSpec.fromJson(s.readText()) }.getOrNull()?.also { cache = key to it }
    }

    fun calibration(): Calibration {
        val spec = assets()?.second ?: return Calibration.DEFAULT
        return runCatching { Models.loadCalibration(app).takeIf { it.fits(Calibration.TYPE, spec.labels(Calibration.TYPE)) && (!spec.has(Calibration.CATEGORY) || it.fits(Calibration.CATEGORY, spec.labels(Calibration.CATEGORY))) } }.getOrNull() ?: Calibration.DEFAULT
    }

    fun warm(): Boolean {
        val model = Models.file(app, Manifest.decide) ?: return false
        if (assets() == null) return false
        val path = model.path
        return try {
            runBlocking { gov.run(Manifest.decide, { DecideRunner.open(app, path) }) { true } }
        } catch (_: Exception) {
            false
        }
    }

    override fun score(raw: Raw): Scored? {
        val model = Models.file(app, Manifest.decide) ?: return null
        val (tok, spec) = assets() ?: return null
        val body = Redact.codes(listOf(raw.title, raw.body).filter { it.isNotBlank() }.joinToString("\n"))
        val cal = calibration()
        val path = model.path
        return try {
            runBlocking {
                gov.run(Manifest.decide, { DecideRunner.open(app, path) }) { r ->
                    fun probs(task: String): Map<String, Float>? = runCatching {
                        val input = Decide.build(spec, task, raw.sender.trim(), body, tok)
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
