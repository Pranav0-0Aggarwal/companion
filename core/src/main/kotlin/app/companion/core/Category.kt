package app.companion.core

enum class Category(val label: String) {
    Food("food"), Groceries("groceries"), Transport("transport"), Shopping("shopping"), Bills("bills"),
    Entertainment("entertainment"), Health("health"), Travel("travel"), Transfer("transfer"), Income("income"), Other("other");

    companion object {
        private val rules = listOf(
            Income to Regex("(?i)salary|payroll|interest|dividend|cashback"),
            Transfer to Regex("(?i)\\bcred\\b|phonepe|gpay|google pay|paytm|bhim|sbi cards?|neft|imps|rtgs|funds? transfer|self|wallet"),
            Travel to Regex("(?i)irctc|makemytrip|goibibo|ibibo|indigo|air india|vistara|redbus|hotel|oyo|airline|cleartrip|yatra|easemytrip|ixigo|airbnb|akasa|spicejet|\\bflight|travel|holiday|resort"),
            Food to Regex("(?i)swiggy|zomato|restaurant|cafe|coffee|tokai|starbucks|domino|pizza|kfc|mcdonald|burger|dining|bistro|\\bfoods?\\b|cater|kitchen|bakery|bakers|sweets?\\b|chai|\\btea\\b|biryani|dhaba|brew|\\bpub\\b|eatery|juice|baskin|boba|dessert|canteen|tiffin|snacks?\\b|blinkit|zepto|instamart|haldiram|subway|barbeque|chaayos|third wave|theobroma|ice ?cream|shawarma|momo"),
            Groceries to Regex("(?i)bigbasket|dmart|supermarket|reliance fresh|grocer|\\bfresh\\b|\\bmart\\b|kirana|provision|vegetable|fruit|dairy|milk|horticulture|jiomart|grofers|more retail|spencer|nature'?s basket"),
            Transport to Regex("(?i)uber|\\bola\\b|rapido|shell|petrol|fuel|metro|fastag|indian oil|bharat petroleum|hp pay|bpcl|iocl|hpcl|parking|\\btoll|\\bcab\\b|taxi|namma|bmtc|vaahan|zoomcar|bykemania|yulu|bounce|auto"),
            Bills to Regex("(?i)airtel|jio|vodafone|\\bvi\\b|electric|bescom|tata power|broadband|fibernet|recharge|insurance|\\brent\\b|\\bgas\\b|water|postpaid|\\bdth\\b|utilit|bbps|bharat bill|\\blic\\b|premium|municipal|property tax|society|maintenance|school|tuition|fees?\\b|google cloud|aws|subscription"),
            Entertainment to Regex("(?i)netflix|spotify|hotstar|prime video|bookmyshow|youtube|sony liv|gaana|jiocinema|\\bpvr\\b|inox|disney|zee5|steam|playstation|gaming"),
            Health to Regex("(?i)pharma|apollo|medplus|1mg|hospital|clinic|diagnostic|\\blab\\b|health|medic|dental|netmeds|practo|wellness|\\bcare\\b|doctor|dr\\.? "),
            Shopping to Regex("(?i)amazon|\\ba\\.in\\b|\\bapay\\b|flipkart|myntra|ajio|nykaa|meesho|decathlon|ikea|enterprise|\\bstores?\\b|retail|fashion|\\bmall\\b|trading|\\bshop|boutique|emporium|garment|textile|jewel|electronic|paypal|lenskart|croma|snitch|cliq|tira\\b|pantaloons|westside|lifestyle|asspl|bazaar|market|sports|footwear|furniture|gift"),
        )

        fun of(merchant: String?, credit: Boolean = false): Category {
            if (merchant == null) return Other
            return rules.firstOrNull { it.second.containsMatchIn(merchant) }?.first
                ?: if (credit) Income else Other
        }

        fun of(m: Event.Move): Category {
            val c = of(m.merchant, m is Event.Credit)
            return if (c == Other && m is Event.Debit && m.mode != Mode.Card) Transfer else c
        }
    }
}
