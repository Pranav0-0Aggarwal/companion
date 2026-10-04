package app.companion.ai

import android.content.Context
import app.companion.core.Calibration
import app.companion.core.Weights
import java.io.File

object Xnn {
    fun path(c: Context, name: String) = File(c.noBackupFilesDir, name).path

    fun prune(c: Context) {
        val keep = HashSet<String>()
        val plan = Active.bert(c)
        if (plan != null) {
            listOf(Calibration.TYPE, Calibration.CATEGORY).filter(plan.spec::has).forEach { keep += Weights.bert(it, plan.shas.getValue(plan.spec.file(it))) }
        } else {
            Models.sha(c, Manifest.decide)?.let { keep += Weights.decide(it) }
        }
        val dir = c.noBackupFilesDir
        Weights.stale(dir.list().orEmpty().toList(), keep).forEach { File(dir, it).delete() }
    }
}
