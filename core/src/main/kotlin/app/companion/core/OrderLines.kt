package app.companion.core

object OrderLines {
    private const val MAX = 20

    private val price = Regex("(?i)(?:₹|\\brs\\.?|\\binr)\\s*\\d[\\d,]*(?:\\.\\d+)?(?:\\s*/-)?")
    private val skip = Regex(
        "(?i)\\b(?:totals?|subtotal|sub total|taxes|tax|gst|cgst|sgst|igst|vat|fees?|delivery|deliver|discounts?|coupon|promo|savings?|saved|packaging|packing|charges?|tip|address|payment|paid|invoice|bill|receipt|" +
            "thanks?|thank you|hello|dear|regards|welcome|orders?|ordered|amount|round off|cod|upi|wallet|otp|pincode|pin code|landmark|nagar|apartments?|phone|mobile|email|estimated|arriving|track|unsubscribe|" +
            "restaurant|customer|instructions?|summary|details|price|quantity)\\b|@|https?:|www\\.|\\b\\d{6}\\b|\\b\\d{10}\\b|\\d{1,2}:\\d{2}|\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}",
    )
    private val junk = Regex("^[\\W_]*$|^(?:(?:item|items|description|qty|quantity|price|amount|name|sr|no|s)\\b\\W*)+$", RegexOption.IGNORE_CASE)
    private val signal = Regex("(?i)\\b(?:order|total|subtotal|qty|quantity|delivery|invoice|bill|payment|paid|gst|receipt)\\b|₹|\\brs\\.?\\s*\\d|\\binr\\b")
    private val lead = Regex("^[\\s\\-*•·–—▪●>]+|^\\d{1,2}[.)]\\s+(?=[\\p{L}\\d])")
    private val trail = Regex("[\\s\\-–—:|,.]+$")
    private val times = Regex("^(\\d{1,2})\\s*(?:[×*]|x(?=\\s))\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val qty = Regex("^qty\\s*[:\\-]?\\s*(\\d{1,2})\\s*(?:[×x*]\\s*)?(.+)$", RegexOption.IGNORE_CASE)
    private val tail = Regex("^(.+?)\\s+[×x*]\\s*(\\d{1,2})$", RegexOption.IGNORE_CASE)
    private val paren = Regex("^(.+?)\\s*\\(\\s*(?:qty\\s*[:\\-]?\\s*)?[×x]?\\s*(\\d{1,2})\\s*\\)$", RegexOption.IGNORE_CASE)
    private val lone = Regex("^(\\d{1,2})\\s+(\\p{L}.*)$")
    private val group = Regex("\\(([^()]*)\\)")
    private val mod = Regex("^(?:extra|no|add|with|without|double)\\b", RegexOption.IGNORE_CASE)
    private val inline = Regex("\\s+((?:with|without|extra|no|add|double)\\s.+)$", RegexOption.IGNORE_CASE)
    private val prefixes = setOf("with", "without", "extra", "no", "add", "double")
    private val measure = Regex("^(?:\\d+(?:\\.\\d+)?\\s*(?:ml|g|gm|gms|l|oz|kg|pcs?|pieces?)|serves\\s*\\d+)$", RegexOption.IGNORE_CASE)
    private val small = setOf("with", "and", "of", "in", "n", "the", "a", "&", "w/")

    fun parse(text: String): List<Req> = parse(text, null)

    fun parse(text: String, brand: String?): List<Req> {
        val sig = signal.containsMatchIn(text)
        return text.lineSequence().mapNotNull { line(it, sig, brand) }.take(MAX).toList()
    }

    private fun line(raw: String, sig: Boolean, brand: String?): Req? {
        if (raw.length > 160) return null
        val s = price.replace(raw.replace(Regex("\\s+"), " ").replace(Regex("(?i)\\bw/\\s*"), "with "), " ")
            .replace(Regex("\\s+"), " ").let { lead.replace(it.trim(), "") }.let { trail.replace(it, "") }.trim()
        if (s.isEmpty() || junk.matches(s) || skip.containsMatchIn(s)) return null
        val (n, name) = lead(s, sig) ?: return null
        return build(name, n, brand)
    }

    private fun lead(s: String, sig: Boolean): Pair<Int, String>? {
        times.find(s)?.let { return it.groupValues[1].toInt() to it.groupValues[2] }
        qty.find(s)?.let { return it.groupValues[1].toInt() to it.groupValues[2] }
        tail.find(s)?.let { return it.groupValues[2].toInt() to it.groupValues[1] }
        paren.find(s)?.let { return it.groupValues[2].toInt() to it.groupValues[1] }
        if (!sig && !structured(s)) return null
        lone.find(s)?.let { return it.groupValues[1].toInt() to it.groupValues[2] }
        return if (titled(s.split(Regex("\\s\\+|\\s-\\s|\\(")).first())) 1 to s else null
    }

    private fun structured(s: String) = " - " in s || '+' in s || '(' in s

    private fun titled(s: String): Boolean {
        val w = s.trim().split(' ')
        return w.size <= 7 && s.none { it in ".!?;:" } && w.all { !it.first().isLetter() || it.first().isUpperCase() || it.lowercase() in small }
    }

    private fun phrases(s: String): List<String> {
        val out = mutableListOf<String>()
        var prev = true
        for (w in s.lowercase().split(' ').filter { it.isNotEmpty() }) {
            val p = w in prefixes
            if (out.isEmpty() || p && !prev) out += w else out[out.lastIndex] += " $w"
            prev = p
        }
        return out
    }

    private fun build(raw: String, n: Int, brand: String?): Req? {
        if (n !in 1..20) return null
        var size: String? = null
        val mods = mutableListOf<String>()
        fun part(p: String): String? {
            val t = p.trim()
            return when {
                t.isEmpty() || measure.matches(t) -> null
                Portion.size(t) != null -> { size = Portion.size(t); null }
                mod.containsMatchIn(t) -> { mods += t.lowercase(); null }
                else -> t
            }
        }
        var base = group.replace(raw) { m -> m.groupValues[1].split(',').mapNotNull(::part).joinToString(" ").let { if (it.isEmpty()) "" else " $it" } }.trim()
        val plus = base.split(Regex("\\s*\\+\\s*"))
        base = plus.first()
        for (p in plus.drop(1)) {
            val t = p.trim()
            if (t.isNotEmpty()) mods += if (mod.containsMatchIn(t)) t.lowercase() else "add ${t.lowercase()}"
        }
        val dash = base.split(Regex("\\s+[-–—]\\s+|\\s*,\\s*")).map { it.trim() }.filter { it.isNotEmpty() }
        var name = dash.firstOrNull() ?: return null
        for (p in dash.drop(1)) {
            val w = p.split(' ', limit = 2)
            val sz = Portion.size(w[0])
            when {
                mod.containsMatchIn(p) -> mods += p.lowercase()
                sz != null && !(w[0].length == 1) -> {
                    size = sz
                    w.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { mods += "with ${it.lowercase()}" }
                }
                else -> name += " $p"
            }
        }
        inline.find(name)?.let { m ->
            mods += phrases(m.groupValues[1])
            name = name.substring(0, m.range.first)
        }
        name = trail.replace(name, "").replace(Regex("\\s+"), " ").trim()
        if (name.length !in 2..60 || name.none { it.isLetter() }) return null
        return Req(name, brand, n.toDouble(), null, size, mods.filter { it.length <= 40 }.distinct().take(5))
    }
}
