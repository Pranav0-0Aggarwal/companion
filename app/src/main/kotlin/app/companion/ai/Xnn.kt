package app.companion.ai

import android.content.Context
import app.companion.core.Calibration
import app.companion.core.Weights
import java.io.File

object Xnn {
    fun path(c: Context, name: String) = File(c.noBackupFilesDir, name).path

    fun prune(c: Context) {
        val plan = Active.bert(c)
        val keep = plan?.let { p -> listOf(Calibration.TYPE, Calibration.CATEGORY).filter(p.spec::has).map { Weights.bert(it, p.shas.getValue(p.spec.file(it))) }.toSet() }.orEmpty()
        val dir = c.noBackupFilesDir
        Weights.stale(dir.list().orEmpty().toList(), keep).forEach { File(dir, it).delete() }
    }
}
