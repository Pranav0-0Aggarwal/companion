package app.companion.core

object Types {
    fun of(k: Kind) = when (k) {
        Kind.Otp -> "otp"
        Kind.Debit, Kind.CardSpend -> "expense"
        Kind.Credit -> "income"
        Kind.Bill, Kind.Statement -> "bill"
        Kind.Delivery -> "delivery"
        Kind.Travel, Kind.Alert, Kind.Unknown -> "alert"
        Kind.Personal -> "personal"
        Kind.Promo -> "promo"
    }
}
