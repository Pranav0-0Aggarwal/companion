package app.companion.core

val BankNames: List<String> = listOf(
    "HDFC Bank", "ICICI Bank", "SBI", "Axis Bank", "Kotak Bank", "IDFC First Bank", "Yes Bank", "IndusInd Bank", "PNB",
    "Bank of Baroda", "Canara Bank", "Federal Bank", "AU Bank", "RBL Bank", "Amex", "Citi", "HSBC", "IDBI Bank", "Standard Chartered",
)

internal object Brands {
    private class Entry(val key: String, val name: String)

    private val banks = listOf(
        Entry("hdfc", "HDFC Bank"), Entry("icici", "ICICI Bank"), Entry("sbi", "SBI"), Entry("axis", "Axis Bank"),
        Entry("kotak", "Kotak Bank"), Entry("idfc", "IDFC First Bank"), Entry("yesbnk", "Yes Bank"), Entry("yes bank", "Yes Bank"),
        Entry("indusind", "IndusInd Bank"), Entry("pnb", "PNB"), Entry("barod", "Bank of Baroda"), Entry("canara", "Canara Bank"),
        Entry("federal", "Federal Bank"), Entry("aubank", "AU Bank"), Entry("rbl", "RBL Bank"), Entry("amex", "Amex"),
        Entry("citi", "Citi"), Entry("hsbc", "HSBC"), Entry("idbi", "IDBI Bank"), Entry("scb", "Standard Chartered"),
    )

    private val shops = listOf(
        Entry("amazon pay", "Amazon Pay"), Entry("amazon", "Amazon"), Entry("flipkart", "Flipkart"), Entry("myntra", "Myntra"),
        Entry("swiggy", "Swiggy"), Entry("zomato", "Zomato"), Entry("blinkit", "Blinkit"), Entry("zepto", "Zepto"),
        Entry("bigbasket", "BigBasket"), Entry("uber", "Uber"), Entry("ola", "Ola"), Entry("rapido", "Rapido"),
        Entry("irctc", "IRCTC"), Entry("makemytrip", "MakeMyTrip"), Entry("airtel", "Airtel"), Entry("jio", "Jio"),
        Entry("netflix", "Netflix"), Entry("spotify", "Spotify"), Entry("google pay", "GPay"), Entry("gpay", "GPay"),
        Entry("phonepe", "PhonePe"), Entry("paytm", "Paytm"), Entry("cred", "CRED"), Entry("indmoney", "INDmoney"),
        Entry("google", "Google"), Entry("delhivery", "Delhivery"), Entry("bluedart", "Blue Dart"), Entry("dtdc", "DTDC"),
        Entry("ekart", "Ekart"), Entry("indigo", "IndiGo"), Entry("vistara", "Vistara"), Entry("air india", "Air India"),
    )

    private val sender = Regex("^[A-Za-z]{2}-|-[A-Za-z]$")

    fun clean(s: String) = s.replace(sender, "").replace(sender, "")

    private fun word(key: String) = Regex("(?i)(?<![a-z])${Regex.escape(key)}(?![a-z])")

    fun bank(sender: String, body: String): String? {
        val s = clean(sender).lowercase()
        return banks.firstOrNull { s.contains(it.key) }?.name ?: banks.firstOrNull { word(it.key).containsMatchIn(body) }?.name
    }

    fun shop(sender: String, body: String): String? {
        val s = clean(sender).lowercase()
        return shops.firstOrNull { it.key.length > 4 && s.contains(it.key) }?.name
            ?: shops.firstOrNull { word(it.key).containsMatchIn(body) }?.name
    }

    fun service(sender: String, body: String): String? {
        shop(sender, body)?.let { return it }
        bank(sender, body)?.let { return it }
        val s = clean(sender)
        return s.takeIf { it.length in 3..14 && it.all(Char::isLetter) }?.lowercase()?.replaceFirstChar(Char::uppercase)
    }
}
