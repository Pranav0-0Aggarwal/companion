package app.companion.core

class Brand(val name: String, val aliases: List<String>, val cat: String, val color: String, val icon: String?, val bank: Boolean)

class BrandDb(val brands: List<Brand>) {
    private class Seq(val words: List<String>, val brand: Brand) {
        val len = words.sumOf { it.length }
    }

    private val exact = HashMap<String, Brand>()
    private val first = HashMap<String, List<Seq>>()
    private val byName = brands.associateBy { it.name.lowercase() }

    init {
        val seqs = ArrayList<Seq>()
        brands.forEach { b -> b.aliases.forEach { a -> words(a).takeIf { it.isNotEmpty() }?.let { w -> exact.putIfAbsent(w.joinToString(" "), b); exact.putIfAbsent(w.joinToString(""), b); seqs += Seq(w, b) } } }
        seqs.groupBy { it.words[0] }.forEach { (k, v) -> first[k] = v.sortedByDescending { it.len } }
    }

    fun named(name: String) = byName[name.lowercase()]

    fun exact(text: String, want: (Brand) -> Boolean = { true }): Brand? {
        val w = words(text)
        return (exact[w.joinToString(" ")] ?: exact[w.joinToString("")])?.takeIf(want)
    }

    fun stem(sender: String, want: (Brand) -> Boolean = { true }): Brand? {
        exact(sender, want)?.let { return it }
        val k = words(sender).joinToString("")
        for (n in k.length - 1 downTo 3) {
            val b = exact[k.substring(0, n)]?.takeIf(want) ?: continue
            if (n >= if (b.bank) 3 else 5) return b
        }
        return null
    }

    fun find(text: String, min: Int = 1, want: (Brand) -> Boolean = { true }): Brand? {
        val w = words(text)
        var best: Seq? = null
        for (i in w.indices) {
            val c = first[w[i]] ?: continue
            for (s in c) {
                if (s.len < min || best != null && s.len <= best.len || !want(s.brand) || i + s.words.size > w.size) continue
                if (s.words.indices.all { w[i + it] == s.words[it] }) best = s
            }
        }
        return best?.brand
    }

    fun merchant(text: String) = exact(text) ?: find(text, MIN_CONTAINED)

    companion object {
        const val MIN_CONTAINED = 4

        fun words(s: String): List<String> {
            val out = ArrayList<String>()
            var from = -1
            for (i in 0..s.length) {
                val c = if (i < s.length) s[i] else ' '
                if (c.isLetterOrDigit()) {
                    if (from < 0) from = i
                } else if (from >= 0) {
                    out += s.substring(from, i).lowercase()
                    from = -1
                }
            }
            return out
        }

        @Suppress("UNCHECKED_CAST")
        fun parse(text: String): BrandDb = BrandDb(
            (Json.obj(text)["brands"] as List<Map<String, Any?>>).map {
                Brand(
                    it["name"] as String, (it["aliases"] as List<String>), it["cat"] as String, it["color"] as String,
                    it["icon"] as String?, it["type"] == "bank",
                )
            },
        )
    }
}
