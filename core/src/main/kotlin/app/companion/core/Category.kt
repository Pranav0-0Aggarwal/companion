package app.companion.core

enum class Category(val label: String) {
    Food("food"), Groceries("groceries"), Transport("transport"), Shopping("shopping"), Bills("bills"),
    Entertainment("entertainment"), Health("health"), Travel("travel"), Transfer("transfer"), Income("income"), Other("other");

    companion object {
        private val rules = listOf(
            Income to Regex("(?i)salary|payroll|interest|dividend|cashback"),
            Food to Regex("(?i)swiggy|zomato|restaurant|cafe|coffee|tokai|starbucks|domino|pizza|kfc|mcdonald|burger|dining"),
            Groceries to Regex("(?i)bigbasket|blinkit|zepto|instamart|grocer|dmart|supermarket|reliance fresh"),
            Transport to Regex("(?i)uber|ola\\b|rapido|shell|petrol|fuel|metro|fastag|indian oil|bharat petroleum|hp pay"),
            Shopping to Regex("(?i)amazon|flipkart|myntra|ajio|nykaa|meesho|decathlon|ikea"),
            Bills to Regex("(?i)airtel|jio|vodafone|\\bvi\\b|electric|bescom|tata power|broadband|recharge|insurance|rent|gas|water|postpaid|dth"),
            Entertainment to Regex("(?i)netflix|spotify|hotstar|prime video|bookmyshow|youtube|sony liv|gaana"),
            Health to Regex("(?i)pharma|apollo|medplus|1mg|hospital|clinic|diagnostic|lab"),
            Travel to Regex("(?i)irctc|makemytrip|goibibo|indigo|air india|vistara|redbus|hotel|oyo|airline"),
        )

        fun of(merchant: String?, credit: Boolean = false): Category {
            if (merchant == null) return Other
            return rules.firstOrNull { it.second.containsMatchIn(merchant) }?.first
                ?: if (credit) Income else Other
        }
    }
}
