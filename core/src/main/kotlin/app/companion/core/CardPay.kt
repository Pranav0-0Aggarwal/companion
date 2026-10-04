package app.companion.core

object CardPay {
    private val payee = Regex(
        "(?i)\\bcred\\b|cred\\s*club|\\bcc\\b|credit\\s*cards?|\\bamex\\b|american\\s*express|\\bone\\s*cards?\\b|\\bslice\\b|" +
            "(?:hdfc|icici|axis|kotak|sbi|yes|idfc|rbl|indusind|au|hsbc|citi|federal|bob|pnb|canara|standard\\s*chartered)\\s*(?:bank\\s*)?cards?\\b",
    )

    fun of(kind: String, category: String?, who: String?) =
        kind == Kind.Debit.name && category == Category.Bills.label && who != null && payee.containsMatchIn(who)
}
