package app.companion.core

object Rules {
    const val MIN = 1

    fun bump(label: String?, count: Int, chosen: String) = if (label == chosen) count + 1 else 1

    fun merge(a: Pair<String, Int>, b: Pair<String, Int>) = when {
        a.first == b.first -> a.first to a.second + b.second
        b.second > a.second -> b
        else -> a
    }

    fun over(label: String, e: Event, raw: Raw? = null): Event? = when (label) {
        "promo" -> Event.Promo.takeUnless { e is Event.Move }
        "spam" -> Event.Spam.takeUnless { e is Event.Move }
        "alert" -> Event.Alert.takeUnless { e is Event.Move }
        "personal" -> Event.Personal.takeUnless { e is Event.Move || e is Event.Otp || e is Event.Bill || e is Event.Statement }
        "delivery" -> if (e is Event.Move || e is Event.Otp) null else Labels.event(label, e, raw)
        "expense", "income", "bill" -> if (e is Event.Otp) null else Labels.event(label, e, raw)
        else -> null
    }

    fun apply(v: Verdict, rules: Map<String, String>, raw: Raw? = null): Pair<Verdict, String?> {
        var e = v.event
        var hit = false
        rules[Calibration.TYPE]?.let { t ->
            val over = over(t, e, raw)
            if (over != null) {
                e = over
                hit = true
            } else if (Types.of(e.kind) == t) {
                hit = true
            }
        }
        val cat = rules[Calibration.CATEGORY]?.takeIf { e is Event.Move }
        val out = if ((hit || cat != null) && !(v is Verdict.Sure && e == v.event)) Verdict.Sure(e, 1f, v.guess, v.tags, v.cat) else v
        return out to cat
    }
}

object Senders {
    const val MIN = 3
    private val labels = setOf("promo", "spam", "alert", "personal", "delivery")
    private val open = setOf(Kind.Unknown, Kind.Alert, Kind.Promo, Kind.Spam, Kind.Personal, Kind.Delivery)

    fun key(sender: String) = Fingerprint.norm(Template.brand(sender, ""))

    fun name(key: String) = key.replaceFirstChar(Char::uppercase)

    fun counts(label: String) = label in labels

    fun bump(label: String?, count: Int, chosen: String) = Rules.bump(label, count, chosen)

    fun apply(v: Verdict, label: String?, raw: Raw? = null): Verdict {
        val e = v.event
        if (label == null || label !in labels || e.kind !in open) return v
        val over = Rules.over(label, e, raw) ?: return v
        return if (v is Verdict.Sure && over == e) v else Verdict.Sure(over, 1f, v.guess, v.tags, v.cat)
    }
}
