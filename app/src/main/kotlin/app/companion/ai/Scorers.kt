package app.companion.ai

import android.app.Application
import app.companion.core.Calibration
import app.companion.core.Raw
import app.companion.core.Scored
import app.companion.core.Scorer

class Scorers(private val app: Application, private val decide: DecideScorer, private val bert: BertScorer) : Scorer {
    private fun on() = Active.bert(app) != null

    fun calibration(): Calibration = if (on()) bert.calibration() else decide.calibration()

    fun warm(): Boolean = if (on()) bert.warm() else decide.warm()

    override fun score(raw: Raw): Scored? = if (on()) bert.score(raw) else decide.score(raw)

    override fun scoreAll(raws: List<Raw>): List<Scored?> = if (on()) bert.scoreAll(raws) else raws.map(decide::score)
}
