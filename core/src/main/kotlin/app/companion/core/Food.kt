package app.companion.core

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class Req(
    val name: String,
    val brand: String? = null,
    val qty: Double = 1.0,
    val unit: String? = null,
    val size: String? = null,
    val mods: List<String> = emptyList(),
) {
    companion object {
        private val none = setOf("", "unknown", "none", "na", "n a", "null", "nil", "generic", "no brand")

        fun brand(b: String?) = b?.trim()?.takeUnless { Words.norm(it) in none }
    }
}

data class Sku(
    val key: String,
    val brand: String?,
    val name: String,
    val kcal: Double,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val per: String = "serving",
) {
    companion object {
        fun key(brand: String?, name: String) = Words.norm(brand.orEmpty()) + "|" + Words.norm(name)
    }
}

enum class Origin(val label: String) {
    Sku("sku"), Brand("brand"), Db("db"), Asked("asked"), Estimate("estimate");

    val badge get() = when (this) {
        Sku, Asked -> "your food"
        Brand, Db -> "menu value"
        Estimate -> "estimate"
    }

    companion object {
        fun of(label: String) = entries.firstOrNull { it.label == label }
    }
}

sealed interface Res

data class Hit(
    val name: String,
    val brand: String?,
    val qty: Double,
    val unit: String,
    val kcal: Double,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val source: Origin,
    val conf: Double,
) : Res

data class Ask(val question: String, val req: Req, val size: Boolean = false) : Res

internal object Words {
    private val marks = Regex("[\\u0300-\\u036f]")
    private val gap = Regex("[^\\p{L}\\p{N}\\p{M}]+")
    private val stop = setOf("the", "a", "an", "of", "with", "and", "or", "from", "at", "in", "for", "my", "some", "ka", "ki", "ke", "wala", "wali", "wale", "ghar", "home", "homemade")

    fun norm(s: String): String =
        gap.replace(marks.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "").replace("'", "").replace("’", ""), " ").trim()

    fun toks(s: String): List<String> = norm(s).split(' ').filter { it.isNotEmpty() && it !in stop }.map(::stem).distinct()

    private fun stem(t: String) = when {
        t.length > 4 && t.endsWith("ies") -> t.dropLast(3) + "y"
        t.length > 4 && (t.endsWith("ches") || t.endsWith("shes") || t.endsWith("sses") || t.endsWith("xes")) -> t.dropLast(2)
        t.length > 3 && t.endsWith("s") && !t.endsWith("ss") -> t.dropLast(1)
        else -> t
    }

    private fun lev(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }

    private fun sim(a: String, b: String): Double {
        if (a == b) return 1.0
        val lo = min(a.length, b.length)
        val hi = max(a.length, b.length)
        if (hi < 4) return 0.0
        if (lo >= 4 && hi - lo <= 2 && (a.startsWith(b) || b.startsWith(a))) return 0.8
        return when (lev(a, b)) {
            1 -> 0.85
            2 -> if (hi >= 7) 0.7 else 0.0
            else -> 0.0
        }
    }

    fun score(q: List<String>, c: List<String>): Double {
        if (q.isEmpty() || c.isEmpty()) return 0.0
        val cq = q.sumOf { x -> c.maxOf { sim(x, it) } } / q.size
        val cc = c.sumOf { y -> q.maxOf { sim(y, it) } } / c.size
        return cq * cc
    }
}

private object Mods {
    val table = mapOf(
        "extra cheese" to 60.0, "add cheese" to 60.0, "with cheese" to 60.0, "double cheese" to 60.0, "no cheese" to -60.0,
        "extra paneer" to 80.0, "add paneer" to 80.0, "extra chicken" to 90.0, "add chicken" to 90.0,
        "extra egg" to 70.0, "add egg" to 70.0, "with egg" to 70.0,
        "extra butter" to 45.0, "add butter" to 45.0, "no butter" to -45.0, "extra ghee" to 45.0,
        "extra mayo" to 90.0, "extra mayonnaise" to 90.0, "with mayo" to 90.0, "no mayo" to -90.0, "no mayonnaise" to -90.0,
        "extra sauce" to 40.0, "extra patty" to 180.0, "double patty" to 180.0,
        "with fries" to 320.0, "add fries" to 320.0, "with coke" to 140.0, "extra rice" to 130.0,
        "extra sugar" to 35.0, "no sugar" to -35.0, "less sugar" to -20.0,
        "add bacon" to 80.0, "extra bacon" to 80.0, "add avocado" to 120.0, "extra veggies" to 20.0, "extra toppings" to 80.0,
        "whipped cream" to 80.0, "extra cream" to 60.0,
    )

    private val neg = Regex("^(?:no|without) ")

    fun delta(mods: List<String>, addons: List<Addon>): Double = mods.sumOf { one(Words.norm(it), addons) }

    private fun one(m: String, addons: List<Addon>): Double {
        val n = neg.containsMatchIn(m)
        val body = neg.replace(m, "")
        addons.firstOrNull { a -> Words.norm(a.name).let { it == body || body.contains(it) || body.length >= 4 && it.contains(body) } }?.let { return if (n) -it.kcal else it.kcal }
        table.entries.filter { it.key in m }.maxByOrNull { it.key.length }?.let { return it.value }
        return if (n || m.startsWith("less ")) 0.0 else 50.0
    }
}

class Resolver(val skus: Map<String, Sku>, val db: FoodDb, val menus: BrandMenus) {
    private val foods = db.foods.map { f -> f to names(f.name, f.aliases) }
    private val items = menus.chains.associate { c -> c.name to c.items.map { i -> i to names(i.name, i.aliases) } }
    private val bowls = setOf("bowl", "plate", "cup")
    private val mass = setOf("g", "ml", "kg", "l")

    fun resolve(r: Req): Res {
        val (brand, item) = split(r)
        (skus[Sku.key(brand, item)] ?: skus[Sku.key(null, item)])?.let { return hit(r, it, Origin.Sku, 1.0) }
        val q = Words.toks(item)
        brand?.let { b -> menus.chain(b) }?.let { c -> menu(r, c, q) }?.let { return it }
        val best = foods.map { (f, ns) -> f to ns.maxOf { Words.score(q, it) } }.maxByOrNull { it.second }
        if (best == null || best.second < 0.6) return Ask("I couldn't find ${r.name.trim().ifEmpty { "that" }}. About how many calories, or should I estimate ${guess(r)}?", r)
        val (f, score) = best
        val u = Portion.unit(r.unit)
        if (r.size.isNullOrBlank() && u in bowls && u in f.serve) return Ask("Small, medium or large $u?", r, true)
        val p = Portion.of(r.qty, r.unit, r.size, f)
        val g = p.grams / 100
        val conf = min(score, if (p.fuzzy) 0.75 else 0.95)
        return fin(r, f.name, brand, u ?: f.unit, f.kcal * g, f.protein * g, f.carbs * g, f.fat * g, 1.0, emptyList(), Origin.Db, conf)
    }

    fun parts(r: Req): List<Req>? {
        if (r.brand != null || skus.isNotEmpty() && (skus[Sku.key(null, r.name.trim())] != null)) return null
        val w = Words.norm(r.name).split(' ').filter { it.isNotEmpty() }
        if (w.size < 2) return null
        val out = mutableListOf<String>()
        var i = 0
        while (i < w.size) {
            val j = (minOf(w.size, i + 3) downTo i + 1).firstOrNull { j -> known(w.subList(i, j).joinToString(" ")) } ?: return null
            out += w.subList(i, j).joinToString(" ")
            i = j
        }
        return out.takeIf { it.size > 1 }?.mapIndexed { k, n -> if (k == 0) r.copy(name = n, mods = emptyList()) else Req(n) }
    }

    private fun known(n: String): Boolean {
        val q = Words.toks(n)
        return q.isNotEmpty() && foods.any { (_, ns) -> ns.any { Words.score(q, it) >= 0.9 } }
    }

    fun guess(r: Req): Int {
        val t = Words.norm(r.name)
        return guesses.firstOrNull { it.first.containsMatchIn(t) }?.second ?: 300
    }

    fun asked(r: Req, kcal: Double, protein: Double? = null, carbs: Double? = null, fat: Double? = null): Sku {
        val (b, n) = split(r)
        return Sku(Sku.key(b, n), b, n, kcal, protein, carbs, fat)
    }

    fun answer(r: Req, kcal: Double, protein: Double? = null, carbs: Double? = null, fat: Double? = null, estimated: Boolean = false): Hit =
        hit(r.copy(mods = emptyList()), asked(r, kcal, protein, carbs, fat), if (estimated) Origin.Estimate else Origin.Asked, if (estimated) 0.5 else 1.0)

    private fun split(r: Req): Pair<String?, String> {
        val m = menus.match(r.name)
        val b = r.brand?.trim()?.takeIf { it.isNotEmpty() }
        return (b?.let { menus.chain(it)?.name ?: it } ?: m?.first?.name) to (m?.second?.takeIf { it.isNotEmpty() } ?: r.name.trim())
    }

    private fun serving(r: Req) = Portion.unit(r.unit)?.takeIf { it !in mass } ?: "serving"

    private fun count(r: Req) = if (Portion.unit(r.unit) in mass || r.qty <= 0) 1.0 else r.qty

    private fun hit(r: Req, s: Sku, origin: Origin, conf: Double): Hit {
        val n = count(r)
        return fin(r, s.name, s.brand, serving(r), s.kcal, s.protein, s.carbs, s.fat, n, emptyList(), origin, conf)
    }

    private fun menu(r: Req, c: Chain, q: List<String>): Hit? {
        val scored = items[c.name].orEmpty().map { (i, ns) -> i to ns.maxOf { Words.score(q, it) } }
        val top = scored.maxOfOrNull { it.second } ?: return null
        if (top < 0.6) return null
        val pool = scored.filter { it.second >= top - 1e-9 }.map { it.first }
        val want = Portion.size(r.size)
        val base = if (want != null) {
            pool.firstOrNull { it.size == want } ?: pool.firstOrNull { it.size == null } ?: pool.minBy { abs(Portion.mult(it.size) - Portion.mult(want)) }
        } else {
            pool.firstOrNull { it.size == null } ?: pool.firstOrNull { it.size == "medium" } ?: pool.first()
        }
        val k = if (want == null || base.size == want) 1.0 else Portion.mult(want) / Portion.mult(base.size)
        val guessed = k != 1.0 || want == null && pool.size > 1
        val n = count(r) * k
        return fin(r, base.name, c.name, serving(r), base.kcal, base.protein, base.carbs, base.fat, n, base.addons, Origin.Brand, min(top, if (guessed) 0.8 else 1.0), count(r))
    }

    private fun fin(
        r: Req, name: String, brand: String?, unit: String, kcal: Double, p: Double?, c: Double?, f: Double?,
        n: Double, addons: List<Addon>, origin: Origin, conf: Double, qty: Double = if (r.qty > 0) r.qty else 1.0,
    ): Hit {
        val delta = Mods.delta(r.mods, addons)
        val cap = if (r.mods.isEmpty()) conf else min(conf, 0.75)
        return Hit(name, brand, qty, unit, r1(max(0.0, kcal * n + delta)), p?.let { r1(it * n) }, c?.let { r1(it * n) }, f?.let { r1(it * n) }, origin, cap)
    }

    private fun r1(x: Double) = Math.round(x * 10) / 10.0

    private fun names(name: String, aliases: List<String>) = (listOf(name) + aliases).map(Words::toks).filter { it.isNotEmpty() }

    private val guesses = listOf(
        Regex("\\b(?:tea|chai|coffee|juice|soda|cola|shake|smoothie|lassi|drink|milk|water|latte|cappuccino|mocha)s?\\b") to 120,
        Regex("\\b(?:salad|soup|fruit|sprout)s?\\b") to 120,
        Regex("\\b(?:cake|gulab|jalebi|ice cream|dessert|sweet|halwa|ladoo|laddu|brownie|pastry|kheer|rasmalai|cookie)s?\\b") to 280,
        Regex("\\b(?:roll|wrap|burrito|sandwich|burger|pizza|momo|biryani|thali|dosa|paratha|curry|rice|noodle|pasta|meal|bowl)s?\\b") to 350,
    )
}
