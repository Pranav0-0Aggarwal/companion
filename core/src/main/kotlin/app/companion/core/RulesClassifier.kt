package app.companion.core

import java.time.ZoneId

class RulesClassifier(private val zone: ZoneId = ZoneId.systemDefault(), private val bar: Float = 0.8f) : Classifier {
    private val money = Regex("(?i)(?<![a-z])(?:rs\\.?|inr|₹)\\s*[0-9]")

    override fun classify(raw: Raw): Verdict {
        val e = Extract.from(raw, zone)
        val c = score(e, raw)
        return if (c >= bar) Verdict.Sure(e, c) else Verdict.Unsure(e, c)
    }

    private fun score(e: Event, r: Raw): Float = when (e) {
        is Event.Otp -> 0.98f
        is Event.Move -> 0.5f + (if (e.last4 != null) 0.3f else 0f) + (if (e.merchant != null) 0.2f else 0f)
        is Event.Bill -> if (e.due != null) 0.95f else 0.7f
        is Event.Statement -> if (e.due != null) 0.97f else 0.75f
        is Event.Delivery, is Event.Travel -> 0.85f
        Event.Promo, Event.Personal -> 0.9f
        Event.Unknown -> if (money.containsMatchIn(r.title + " " + r.body)) 0.4f else 0.8f
    }
}
