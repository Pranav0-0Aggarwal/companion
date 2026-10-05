package app.companion.core

class Scored(val type: Map<String, Float>, val category: Map<String, Float>? = null)

fun interface Scorer {
    fun score(raw: Raw): Scored?

    fun scoreAll(raws: List<Raw>): List<Scored?> = raws.map(::score)
}

object Labels {
    val money = setOf("expense", "income")

    fun kind(label: String, rules: Event): Kind? = when (label) {
        "otp" -> Kind.Otp
        "expense" -> if (rules is Event.CardSpend) Kind.CardSpend else Kind.Debit
        "income" -> Kind.Credit
        "bill" -> if (rules is Event.Statement) Kind.Statement else Kind.Bill
        "delivery" -> Kind.Delivery
        "alert" -> Kind.Alert
        "personal" -> Kind.Personal
        "promo" -> Kind.Promo
        "spam" -> Kind.Spam
        else -> null
    }

    fun event(label: String, rules: Event, raw: Raw? = null): Event? = when (kind(label, rules)) {
        Kind.Otp -> rules as? Event.Otp
        Kind.Debit -> (rules as? Event.Move)?.let { it as? Event.Debit ?: Event.Debit(it.paise, it.currency, it.last4, it.bank, it.merchant, it.mode) }
        Kind.CardSpend -> rules as? Event.CardSpend
        Kind.Credit -> (rules as? Event.Move)?.let { it as? Event.Credit ?: Event.Credit(it.paise, it.currency, it.last4, it.bank, it.merchant, it.mode) }
        Kind.Bill, Kind.Statement -> when (rules) {
            is Event.Bill, is Event.Statement -> rules
            is Event.Move -> Event.Bill(rules.paise, rules.currency, null, null, rules.merchant ?: rules.bank, rules.last4)
            else -> null
        }
        Kind.Delivery -> rules as? Event.Delivery ?: raw?.text()?.let { Event.Delivery(Brands.shop(raw.sender, it), Misc.stage(it) ?: Stage.Update) }
        Kind.Alert -> when (rules) {
            is Event.Travel -> rules
            is Event.Otp, is Event.Move, is Event.Bill, is Event.Statement -> null
            else -> Event.Alert
        }
        Kind.Personal -> if (rules == Event.Personal || rules == Event.Unknown) Event.Personal else null
        Kind.Promo -> Event.Promo.takeUnless { rules is Event.Otp }
        Kind.Spam -> Event.Spam.takeUnless { rules is Event.Otp }
        else -> null
    }

    fun category(label: String): Category? = Category.entries.firstOrNull { it != Category.Income && it.label == label }
}
