package app.companion.core

import java.time.ZoneId

class Refine(
    private val rules: RulesClassifier,
    private val extractor: Extractor,
    private val cal: () -> Calibration,
    private val scorer: Scorer,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    fun run(raw: Raw): Verdict {
        val base = rules.classify(raw)
        val e = Rebuild.of(raw, base.event, found(raw, base.event), zone)
        val v = if (e == base.event) base else rules.judge(e, raw)
        return DecideClassifier({ v }, cal, scorer).classify(raw)
    }

    private fun found(raw: Raw, e: Event): Map<Field, String> {
        val want = Gaps.of(raw, e, zone)
        if (want.isEmpty()) return emptyMap()
        val t = raw.text()
        return extractor.extract(raw, want).filter { (f, v) -> f in want && Verbatim.ok(f, v, t) }
    }
}
