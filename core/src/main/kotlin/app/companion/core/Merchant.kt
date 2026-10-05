package app.companion.core

object Merchant {
    const val MIN = 2

    private val acronyms = setOf("HDFC", "ICICI", "SBI", "IRCTC", "KFC", "OYO", "CRED", "IKEA", "BSNL", "LIC", "IDFC", "RBL", "AU", "HSBC", "DTDC", "BMS")
    private val tail = setOf("pvt", "ltd", "private", "limited", "llp", "inc", "india", "technologies", "payments", "bangalore", "bengaluru", "mumbai", "delhi", "gurgaon", "gurugram", "noida", "hyderabad", "chennai", "pune")
    private val keep = setOf("of", "air")
    private val prefix = Regex("(?i)^(?:upi[-/\\s]+)+")
    private val ref = Regex("^#?\\d{3,}$|^(?=.*\\d)(?=.*[A-Za-z])[A-Za-z0-9]{7,}$")
    private val seq = Regex("(?i)[-/]\\d[\\w-]*$")
    private val gap = Regex("\\s+")
    private val mark = Regex("[*`\"“”]")
    private val edge = Regex("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N})]+$")

    fun source(f: () -> String) {
        Brands.source = f
    }

    private fun tidy(raw: String?): List<String> {
        var s = raw ?: return emptyList()
        s = s.substringBefore('@').replace(mark, " ").replace('_', ' ').replace('’', '\'').replace('‘', '\'')
        s = s.replace(edge, "").replace(prefix, "").replace(seq, "").replace(edge, "")
        val w = s.split(gap).filter { it.isNotEmpty() }.toMutableList()
        while (w.size > 1 && w.last().let { ref.matches(it) }) w.removeAt(w.lastIndex)
        return w
    }

    private fun trim(w: List<String>): List<String> {
        val out = w.toMutableList()
        while (out.size > 1 && out.last().filter(Char::isLetter).lowercase().let { (it in tail || it == "in") && out[out.size - 2].lowercase() !in keep }) out.removeAt(out.lastIndex)
        return out
    }

    private fun case(w: String) = when {
        w.uppercase() in acronyms && (w == w.uppercase() || w.length > 2) -> w.uppercase()
        w.any(Char::isLetter) && (w == w.uppercase() || w == w.lowercase()) -> w.lowercase().let { l -> l.indexOfFirst(Char::isLetter).let { l.substring(0, it) + l[it].uppercaseChar() + l.substring(it + 1) } }
        else -> w
    }

    fun clean(raw: String?): String = trim(tidy(raw)).joinToString(" ", transform = ::case)

    fun resolve(raw: String?, exact: Boolean = false): String? {
        val w = tidy(raw)
        if (w.isEmpty()) return null
        val t = w.joinToString(" ")
        val c = trim(w)
        val brand = if (exact) Brands.db.exact(t) ?: Brands.db.exact(c.joinToString(" ")) else Brands.db.merchant(t) ?: Brands.db.merchant(c.joinToString(" "))
        return (brand?.name ?: c.joinToString(" ", transform = ::case)).takeIf { it.isNotBlank() }
    }

    fun key(s: String) = BrandDb.words(s).joinToString(" ")

    fun brand(name: String?, banks: Boolean = false): Brand? = name?.let { Brands.db.merchant(it, banks) }

    fun guess(merchant: String, sender: String): String? {
        val b = Brands.db.stem(Brands.clean(sender)) { !it.bank && it.cat != Category.Transfer.label && it.cat != Category.Income.label } ?: return null
        return b.name.takeIf { Brands.db.merchant(merchant) == null }
    }

    fun due(seen: Int) = seen >= MIN
}

fun Event.who(): String? = when (this) {
    is Event.Move -> merchant
    is Event.Bill -> biller
    is Event.Delivery -> merchant
    else -> null
}

fun Event.named(n: String): Event = when (this) {
    is Event.Debit -> copy(merchant = n)
    is Event.Credit -> copy(merchant = n)
    is Event.CardSpend -> copy(merchant = n)
    is Event.Bill -> copy(biller = n)
    is Event.Delivery -> copy(merchant = n)
    else -> this
}
