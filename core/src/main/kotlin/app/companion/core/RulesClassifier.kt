package app.companion.core

import java.time.ZoneId

class RulesClassifier(private val zone: ZoneId = ZoneId.systemDefault(), private val bar: Float = 0.97f) : Classifier {
    private val risk = Regex("(?i)mandate|auto-?pay|standing instruction|\\bnach\\b|folio|\\bnav\\b|\\bunits\\b|\\bsip\\b|redeem|switch|cashback|offer|reward|\\bapply\\b|\\bbill\\b|\\bdue\\b|statement|stmt|declin|fail|revers|refund|\\bwill\\b|pending|chargeback|return|cancel|expir|verification")
    private val acct = Regex("(?i)\\b(?:a/c|ac|acct|account)\\b")
    private val card = Regex("(?i)\\bcard\\b")
    private val offer = Regex("(?i)\\bflat\\b|\\d+\\s*%|\\boff\\b|\\bcode\\s*:?\\s*[A-Z0-9]{4,}")
    private val setback = Regex("(?i)unsuccessful|fail|reject|tried|attempt|delay|cancel|resched|undeliver")
    private val lapse = Regex("(?i)declin|fail|revers|refund|chargeback|return|cancel|pending|unsuccessful")
    private val moved = Regex("(?i)credit|debit|received|refund|\\bbill\\b|\\bdues?\\b|statement|emi\\b|application|kyc|otp|added to|loan")

    override fun classify(raw: Raw) = judge(Extract.from(raw, zone), raw)

    fun judge(e: Event, raw: Raw): Verdict {
        val c = score(e, raw)
        return if (c >= bar) Verdict.Sure(e, c) else Verdict.Unsure(e, c)
    }

    private fun score(e: Event, r: Raw): Float {
        val t = r.title + "\n" + r.body
        return when (e) {
            is Event.Otp -> 0.98f
            is Event.Move -> move(e, t)
            is Event.Bill -> if (e.due != null) 0.97f else 0.7f
            is Event.Statement -> if (e.due != null) 0.97f else 0.75f
            is Event.Delivery -> if (e.stage == Stage.Delivered || setback.containsMatchIn(t)) 0.9f else 0.98f
            is Event.Travel -> 0.6f
            Event.Alert -> 0.65f
            Event.Promo -> if (Misc.promoSender(r.sender) && offer.containsMatchIn(t) && !moved.containsMatchIn(t)) 0.975f else 0.8f
            Event.Spam -> 0.6f
            Event.Personal -> 0.6f
            Event.Unknown -> 0.4f
        }
    }

    private fun move(e: Event.Move, t: String): Float {
        val id = e.last4 != null
        val who = e.merchant != null
        val clean = !risk.containsMatchIn(t)
        return when {
            e is Event.Credit && Flows.of(e, t) == Flow.CardBill && !lapse.containsMatchIn(t) -> 0.99f
            !clean -> 0.5f
            e is Event.Credit -> if (id && acct.containsMatchIn(t) && !card.containsMatchIn(t)) 0.99f else 0.9f
            e is Event.CardSpend && (id || who) -> 0.98f
            id && who -> 0.99f
            id || who -> 0.97f
            else -> 0.7f
        }
    }
}
