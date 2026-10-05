package app.companion.core

data class Dish(val items: List<Pair<String, Int>>, val kcal: Int, val protein: Int) {
    fun line() = items.joinToString(" + ") { (n, q) -> if (q > 1) "$q $n" else n } + ": $kcal kcal, $protein g protein"
}

object Plates {
    private val meat = setOf("chicken_curry", "egg_bhurji", "omelette", "boiled_egg")
    private val curries = listOf("dal_tadka", "rajma", "chole", "palak_paneer", "paneer_butter_masala", "sabzi", "chicken_curry", "egg_bhurji")
    private val whole = mapOf("khichdi" to 1, "idli" to 3, "masala_dosa" to 1, "poha" to 1, "upma" to 1, "omelette" to 2, "boiled_egg" to 3)
    private val bases = listOf("roti" to 2, "rice" to 1)

    fun of(db: FoodDb, max: Int?, veg: Boolean, n: Int = 4): List<Dish> {
        val by = db.foods.associateBy { it.id }
        fun part(id: String, q: Int) = by[id]?.let { f -> Triple(f, q, (f.serve[f.unit] ?: 100.0) * q / 100) }
        val cap = max ?: 700
        return (curries.flatMap { c -> bases.map { listOf(c to 1, it) } } + whole.map { listOf(it.toPair()) })
            .filter { p -> !veg || p.none { it.first in meat } }
            .flatMap { p -> listOf(p, p + ("curd" to 1)) }
            .mapNotNull { p ->
                val parts = p.map { (id, q) -> part(id, q) ?: return@mapNotNull null }
                Dish(parts.map { it.first.name to it.second }, parts.sumOf { it.first.kcal * it.third }.toInt(), parts.sumOf { it.first.protein * it.third }.toInt())
            }
            .filter { it.kcal <= cap }
            .sortedByDescending { it.protein }
            .distinctBy { it.items.first().first }
            .take(n)
    }
}
