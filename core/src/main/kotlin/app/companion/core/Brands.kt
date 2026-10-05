package app.companion.core

val BankNames: List<String> = listOf(
    "HDFC Bank", "ICICI Bank", "SBI", "Axis Bank", "Kotak Bank", "IDFC First Bank", "Yes Bank", "IndusInd Bank", "PNB",
    "Bank of Baroda", "Canara Bank", "Federal Bank", "AU Bank", "RBL Bank", "Amex", "Citi", "HSBC", "IDBI Bank", "Standard Chartered",
)

internal object Brands {
    @Volatile var source: () -> String = { Brands::class.java.getResourceAsStream("/brands.json")?.use { it.readBytes().decodeToString() } ?: "{\"brands\":[]}" }

    val db: BrandDb by lazy { BrandDb.parse(source()) }

    private val sender = Regex("^[A-Za-z]{2}-|-[A-Za-z]$")

    private val header = Regex("^[A-Za-z]{2}-[A-Za-z0-9&]{3,9}(?:-[A-Za-z])?$|^[A-Z][A-Z0-9]{3,8}$")

    fun header(sender: String): String? {
        val s = sender.trim().takeIf { header.matches(it) } ?: return null
        val stem = clean(s)
        return db.stem(stem)?.name ?: stem.uppercase()
    }

    fun clean(s: String) = s.replace(sender, "").replace(sender, "")

    fun bank(sender: String, body: String): String? = db.stem(clean(sender)) { it.bank }?.name ?: db.find(body, 3) { it.bank }?.name

    fun shop(sender: String, body: String): String? = db.stem(clean(sender)) { !it.bank }?.name ?: db.find(body, 3) { !it.bank }?.name

    fun service(sender: String, body: String): String? {
        shop(sender, body)?.let { return it }
        bank(sender, body)?.let { return it }
        val s = clean(sender)
        return s.takeIf { it.length in 3..14 && it.all(Char::isLetter) }?.lowercase()?.replaceFirstChar(Char::uppercase)
    }
}
