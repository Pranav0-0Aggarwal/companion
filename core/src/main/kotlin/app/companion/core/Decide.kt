package app.companion.core

class DecideInput(val ids: IntArray, val mask: IntArray) {
    val bucket get() = ids.size
}

object Decide {
    fun softmax(x: FloatArray): FloatArray {
        val m = x.max()
        val e = FloatArray(x.size) { kotlin.math.exp(x[it] - m) }
        val s = e.sum()
        return FloatArray(x.size) { e[it] / s }
    }
}

class DecideClassifier(
    private val rules: Classifier,
    private val cal: () -> Calibration = { Calibration.DEFAULT },
    private val scorer: Scorer,
) : Classifier {
    override fun classify(raw: Raw): Verdict {
        val v = rules.classify(raw)
        if (v is Verdict.Sure) return v
        val s = scorer.score(raw) ?: return v
        val c = cal()
        val p = c.pick(Calibration.TYPE, s.type) ?: return v
        val guess = Guess(p.label, p.prob)
        val cat = s.category?.let { c.pick(Calibration.CATEGORY, it) }?.takeIf { it.sure }?.let { Guess(it.label, it.prob) }
        val e = Labels.event(p.label, v.event, raw)
        return if (p.sure && e != null) {
            Verdict.Sure(e, p.prob, guess, p.tags, cat)
        } else {
            Verdict.Unsure(v.event, maxOf(v.confidence, p.prob.coerceAtMost(CAP)), guess, p.tags, cat)
        }
    }

    private companion object {
        const val CAP = 0.89f
    }
}
