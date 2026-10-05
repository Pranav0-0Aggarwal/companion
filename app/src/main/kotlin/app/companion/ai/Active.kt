package app.companion.ai

import android.content.Context
import app.companion.core.BertPlan
import app.companion.core.BertSpec
import app.companion.core.Calibration
import java.io.File

object Active {
    private val listed = Manifest.bert.associate { it.file to it.sha }
    private var hit: Pair<String, BertPlan?>? = null
    private var cal: Pair<String, Pair<Calibration, String?>>? = null

    private fun stamp(c: Context) = Manifest.bert.joinToString(",") { Models.base(c, it).let { f -> "${f.length()}:${f.lastModified()}" } }

    @Synchronized
    fun bert(c: Context): BertPlan? {
        val stamp = stamp(c)
        hit?.takeIf { it.first == stamp }?.let { return it.second }
        val spec = Models.file(c, Manifest.spec)?.let { runCatching { it.readText() }.getOrNull() }
        val plan = BertPlan.of(spec, listed) { n, sha -> Models.verified(c, File(Models.dir(c), n), sha) }
        hit = stamp to plan
        return plan
    }

    @Synchronized
    fun calibration(c: Context, s: BertSpec): Pair<Calibration, String?> {
        val stamp = stamp(c) + "|" + s.calibration
        cal?.takeIf { it.first == stamp }?.let { return it.second }
        return load(c, s).also { cal = stamp to it }
    }

    private fun load(c: Context, s: BertSpec): Pair<Calibration, String?> {
        val none = Calibration.DEFAULT to null
        val sha = listed[s.calibration] ?: return none
        val f = File(Models.dir(c), s.calibration)
        if (!Models.verified(c, f, sha)) return none
        val k = runCatching { Calibration.fromJson(f.readText()) }.getOrNull() ?: return none
        val ok = k.fits(Calibration.TYPE, s.labels(Calibration.TYPE)) &&
            (!s.has(Calibration.CATEGORY) || k.fits(Calibration.CATEGORY, s.labels(Calibration.CATEGORY))) && (s.model == null || k.model == s.model)
        return if (ok) k to sha else none
    }

    fun sha(c: Context): String? = bert(c)?.let { it.key(calibration(c, it.spec).second) }

    fun version(c: Context): String? = sha(c)?.let { "${it.take(8)} ModernBERT" }
}
