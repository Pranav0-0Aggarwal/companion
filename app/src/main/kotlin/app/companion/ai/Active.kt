package app.companion.ai

import android.content.Context
import app.companion.core.BertPlan
import app.companion.core.BertSpec
import app.companion.core.Calibration
import app.companion.core.Json
import java.io.File

object Active {
    private const val SPEC = "model_spec.json"
    private val hex = Regex("[0-9a-f]{64}")
    private var hit: Pair<String, BertPlan?>? = null

    private fun listed(d: File): Map<String, String> = runCatching {
        Json.obj(File(d, "custom.json").readText()).mapNotNull { (k, v) -> (v as? String)?.lowercase()?.takeIf(hex::matches)?.let { k to it } }.toMap()
    }.getOrDefault(emptyMap())

    @Synchronized
    fun bert(c: Context): BertPlan? {
        val d = Models.custom(c)
        val stamp = d.listFiles().orEmpty().sortedBy { it.name }.joinToString(",") { "${it.name}:${it.length()}:${it.lastModified()}" }
        hit?.takeIf { it.first == stamp }?.let { return it.second }
        val spec = File(d, SPEC).takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }
        val plan = BertPlan.of(spec, listed(d)) { n, sha -> Models.verified(c, File(d, n), sha) }
        hit = stamp to plan
        return plan
    }

    fun calibration(c: Context, s: BertSpec): Pair<Calibration, String?> {
        if (Models.have(c, Manifest.calibration) != Have.Custom) return Calibration.DEFAULT to null
        val k = Models.loadCalibration(c)
        val ok = k !== Calibration.DEFAULT && k.fits(Calibration.TYPE, s.labels(Calibration.TYPE)) &&
            (!s.has(Calibration.CATEGORY) || k.fits(Calibration.CATEGORY, s.labels(Calibration.CATEGORY))) && (s.model == null || k.model == s.model)
        return if (ok) k to Models.sha(c, Manifest.calibration) else Calibration.DEFAULT to null
    }

    fun sha(c: Context): String? = bert(c)?.let { it.key(calibration(c, it.spec).second) } ?: Models.sha(c, Manifest.decide)

    fun version(c: Context): String? = bert(c)?.let { "${it.key(calibration(c, it.spec).second).take(8)} ModernBERT custom" } ?: Models.version(c, Manifest.decide)

    fun label(c: Context): String = if (bert(c) != null) "ModernBERT (custom)" else when (Models.have(c, Manifest.decide)) {
        Have.Custom -> "GLiNER (custom)"
        Have.Base -> "GLiNER (base)"
        Have.No -> "GLiNER (not installed)"
    }
}
