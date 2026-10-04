package app.companion.core

import java.time.ZoneId

class Memo(private val scorer: Scorer) : Scorer {
    private val seen = HashMap<Raw, Scored?>()

    override fun score(raw: Raw): Scored? = if (seen.containsKey(raw)) seen[raw] else scorer.score(raw).also { seen[raw] = it }
}

class Refine(
    private val rules: RulesClassifier,
    private val extractor: Extractor,
    private val cal: () -> Calibration,
    private val scorer: Scorer,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    fun run(raw: Raw, x: Extractor = extractor, s: Scorer = scorer): Verdict {
        val base = rules.classify(raw)
        val want = Gaps.of(raw, base.event, zone)
        fun decide(v: Verdict) = DecideClassifier({ v }, cal, s).classify(raw)
        if (want.isEmpty()) return decide(base)
        val first by lazy { decide(base) }
        val ask = if (want.none(Nux.FIELDS::contains) || Nux.money(first, cal())) want else want - Nux.FIELDS
        val t = raw.text()
        val got = if (ask.isEmpty()) emptyMap() else x.extract(raw, ask).filter { (f, v) -> f in ask && Verbatim.ok(f, v, t) }
        val e = Rebuild.of(raw, base.event, got, zone)
        return if (e == base.event) first else decide(rules.judge(e, raw))
    }
}
