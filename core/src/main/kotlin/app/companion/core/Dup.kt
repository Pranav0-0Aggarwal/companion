package app.companion.core

object Dup {
    const val WINDOW = 45L * 86_400_000L
    const val LIMIT = 20

    private val money = listOf("Debit", "Credit", "CardSpend")
    private val bill = listOf("Bill", "Statement")

    fun kinds(kind: String) = when (kind) {
        in money -> money
        in bill -> bill
        else -> listOf(kind)
    }

    fun compatible(a: String, b: String) = b in kinds(a)
}
