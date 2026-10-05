package app.companion.core

data class Food(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val serve: Map<String, Double>,
    val unit: String,
    val source: String,
)

class FoodDb(val foods: List<Food>) {
    companion object {
        fun parse(json: String): FoodDb {
            val o = Json.obj(json)
            check(o["version"] == 1L) { "food version" }
            sStr(o, "source")
            val foods = sList(o, "foods").map { food(sMap(it)) }
            check(foods.map { it.id }.toSet().size == foods.size) { "food ids" }
            return FoodDb(foods)
        }

        private fun food(m: Map<String, Any?>): Food {
            val p = sMap(m["per100"])
            val serve = sMap(m["serve"]).mapValues { sNum(it.value, 1e-9, 5000.0) }
            val unit = sStr(m, "unit")
            check(serve.isNotEmpty() && unit in serve && serve.keys.all { it in Portion.units }) { "food serve" }
            val kcal = sNum(p["kcal"], 0.0, 900.0)
            val protein = sNum(p["protein"], 0.0, 100.0)
            val carbs = sNum(p["carbs"], 0.0, 100.0)
            val fat = sNum(p["fat"], 0.0, 100.0)
            return Food(sStr(m, "id"), sStr(m, "name"), sStrs(m["aliases"]), kcal, protein, carbs, fat, serve, unit, sStr(m, "source"))
        }
    }
}

data class Addon(val name: String, val kcal: Double)

data class Item(
    val name: String,
    val aliases: List<String>,
    val size: String?,
    val kcal: Double,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val serve: Double?,
    val addons: List<Addon>,
)

data class Chain(val name: String, val aliases: List<String>, val source: String, val asOf: String, val items: List<Item>)

class BrandMenus(val chains: List<Chain>) {
    fun chain(name: String): Chain? = match(name)?.first

    fun match(text: String): Pair<Chain, String>? {
        val t = " ${Words.norm(text)} "
        return names.firstNotNullOfOrNull { (alias, c) -> if (" $alias " in t) c to t.replace(" $alias ", " ").trim() else null }
    }

    private val names = chains.flatMap { c -> (listOf(c.name) + c.aliases).map { Words.norm(it) to c } }.filter { it.first.isNotEmpty() }.sortedByDescending { it.first.length }

    companion object {
        fun parse(json: String): BrandMenus {
            val o = Json.obj(json)
            check(o["version"] == 1L) { "menu version" }
            val chains = sList(o, "chains").map { chain(sMap(it)) }
            check(chains.map { Words.norm(it.name) }.toSet().size == chains.size) { "menu names" }
            return BrandMenus(chains)
        }

        private fun chain(m: Map<String, Any?>): Chain {
            val items = sList(m, "items").map { item(sMap(it)) }
            val source = sStr(m, "source", true)
            val asOf = sStr(m, "as_of", true)
            check(items.isEmpty() || source.isNotBlank() && asOf.isNotBlank()) { "menu source" }
            return Chain(sStr(m, "name"), sStrs(m["aliases"]), source, asOf, items)
        }

        private fun item(m: Map<String, Any?>): Item {
            val size = m["size"]?.let { Portion.size(it as? String) ?: error("item size") }
            val addons = (m["addons"]?.let { sList(m, "addons") } ?: emptyList()).map { sMap(it).let { a -> Addon(sStr(a, "name"), sNum(a["kcal"], -2000.0, 5000.0)) } }
            fun opt(k: String) = m[k]?.let { sNum(it, 0.0, 5000.0) }
            return Item(sStr(m, "name"), sStrs(m["aliases"]), size, sNum(m["kcal"], 0.0, 5000.0), opt("protein"), opt("carbs"), opt("fat"), m["serve"]?.let { sNum(it, 1e-9, 5000.0) }, addons)
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun sMap(v: Any?): Map<String, Any?> = (v as? Map<String, Any?>) ?: error("shape")

private fun sList(m: Map<String, Any?>, k: String): List<Any?> = (m[k] as? List<Any?>) ?: error("list $k")

private fun sStr(m: Map<String, Any?>, k: String, blank: Boolean = false): String =
    (m[k] as? String)?.takeIf { blank || it.isNotBlank() } ?: error("field $k")

private fun sStrs(v: Any?): List<String> = when (v) {
    null -> emptyList()
    is List<*> -> v.map { (it as? String)?.takeIf(String::isNotBlank) ?: error("alias") }
    else -> error("aliases")
}

private fun sNum(v: Any?, lo: Double, hi: Double): Double =
    (v as? Number)?.toDouble()?.takeIf { it.isFinite() && it in lo..hi } ?: error("number")
