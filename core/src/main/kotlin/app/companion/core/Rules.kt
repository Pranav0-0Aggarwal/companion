package app.companion.core

object Rules {
    const val MIN = 2

    fun bump(label: String?, count: Int, chosen: String) = if (label == chosen) count + 1 else 1

    fun apply(v: Verdict, rules: Map<String, String>): Pair<Verdict, String?> {
        var e = v.event
        var hit = false
        rules[Calibration.TYPE]?.let { t ->
            val over = when (t) {
                "promo", "spam" -> Event.Promo
                "alert" -> Event.Alert
                else -> null
            }
            if (over != null && e !is Event.Move) {
                e = over
                hit = true
            } else if (Types.of(e.kind) == t) {
                hit = true
            }
        }
        val cat = rules[Calibration.CATEGORY]?.takeIf { e is Event.Move }
        val out = if ((hit || cat != null) && !(v is Verdict.Sure && e == v.event)) Verdict.Sure(e, 1f, v.guess) else v
        return out to cat
    }
}
