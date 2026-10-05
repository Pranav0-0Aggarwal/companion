package app.companion.core

object Chats {
    const val KEEP = 2 * 86_400_000L
    private val tail = setOf("in", "india", "official", "support", "care", "alerts", "orders", "order", "help", "customer", "service", "delivery", "updates", "business", "team", "ltd", "limited", "pvt", "private")

    fun chat(s: Source) = s == Source.Wa || s == Source.Ig

    fun brand(name: String): Brand? {
        if (name.trim().length < 3) return null
        val w = BrandDb.words(name)
        return (w.size downTo 1).firstNotNullOfOrNull { n ->
            val t = w.take(n).joinToString(" ")
            if (w.drop(n).all { it in tail }) Brands.db.exact(t) ?: Brands.db.named(t) else null
        }
    }

    fun route(r: Raw, vips: Set<String>): Raw {
        if (!chat(r.source) || vips.any { it.equals(r.sender.trim(), true) }) return r
        val b = brand(r.sender) ?: brand(r.title) ?: return r
        return r.copy(sender = b.name, title = "")
    }

    fun quiet(e: Event, r: Raw, vips: Set<String>) = e == Event.Personal && chat(r.source) && !Worth.dm(r.sender, r.title + " " + r.body, vips)

    fun vip(list: String, name: String): String {
        val n = name.trim()
        return if (n.isEmpty() || list.lineSequence().any { it.trim().equals(n, true) }) list else list.trimEnd().let { if (it.isEmpty()) n else "$it\n$n" }
    }
}
