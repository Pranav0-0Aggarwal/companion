package app.companion.core

data class Portion(val grams: Double, val fuzzy: Boolean) {
    companion object {
        val units = setOf("piece", "bowl", "plate", "cup", "glass", "tbsp", "tsp", "slice", "serving")

        private val names = mapOf(
            "piece" to "piece pieces pcs pc nos number numbers unit units each tukda tukde",
            "bowl" to "bowl bowls katori katoris katora katore bowlful vati vaati",
            "plate" to "plate plates plateful thali thaali",
            "cup" to "cup cups pyala pyali",
            "glass" to "glass glasses gilas glas",
            "g" to "g gm gms gram grams gr",
            "kg" to "kg kgs kilo kilos kilogram kilograms",
            "ml" to "ml mls millilitre milliliter",
            "l" to "l lt litre litres liter liters",
            "tbsp" to "tbsp tbs tablespoon tablespoons chammach chamach spoon spoons",
            "tsp" to "tsp teaspoon teaspoons",
            "slice" to "slice slices",
            "serving" to "serving servings portion portions helping",
        ).flatMap { (k, v) -> v.split(' ').map { it to k } }.toMap()

        private val sizes = mapOf(
            "small" to "small chhota chota chhoti choti mini s tall",
            "medium" to "medium regular normal m grande",
            "large" to "large big bada badi bara l venti",
        ).flatMap { (k, v) -> v.split(' ').map { it to k } }.toMap()

        private val scale = mapOf("small" to 0.75, "medium" to 1.0, "large" to 1.35)

        private val dflt = mapOf("bowl" to 150.0, "plate" to 250.0, "cup" to 200.0, "glass" to 250.0, "tbsp" to 15.0, "tsp" to 5.0, "slice" to 30.0)

        private val words = mapOf(
            "half" to 0.5, "aadha" to 0.5, "aadhi" to 0.5, "adha" to 0.5, "aadhaa" to 0.5, "quarter" to 0.25, "paav" to 0.25,
            "ek" to 1.0, "one" to 1.0, "a" to 1.0, "an" to 1.0, "do" to 2.0, "two" to 2.0, "couple" to 2.0, "teen" to 3.0, "three" to 3.0,
            "char" to 4.0, "four" to 4.0, "paanch" to 5.0, "panch" to 5.0, "five" to 5.0, "chhe" to 6.0, "six" to 6.0, "saat" to 7.0, "seven" to 7.0,
            "aath" to 8.0, "eight" to 8.0, "nau" to 9.0, "nine" to 9.0, "das" to 10.0, "ten" to 10.0, "dedh" to 1.5, "derh" to 1.5, "dhai" to 2.5, "dhaai" to 2.5,
        )

        private val vulgar = mapOf('½' to " 1/2", '¼' to " 1/4", '¾' to " 3/4", '⅓' to " 1/3")

        fun unit(s: String?): String? = s?.let { names[Words.norm(it)] }

        fun size(s: String?): String? = s?.let { sizes[Words.norm(it)] }

        fun mult(size: String?): Double = scale[size(size)] ?: 1.0

        fun qty(text: String): Double? {
            val t = vulgar.entries.fold(text.lowercase()) { a, e -> a.replace(e.key.toString(), e.value) }
            val parts = Regex("[^a-z0-9./]+").split(t).filter { it.isNotEmpty() && it != "of" }
            val keep = if (parts.size > 1) parts.filter { it != "and" && it != "a" && it != "an" } else parts
            if (keep.isEmpty()) return null
            val sum = keep.fold(0.0) { a, x -> a + (part(x) ?: return null) }
            return sum.takeIf { it > 0 }
        }

        private fun part(p: String): Double? = words[p] ?: p.toDoubleOrNull() ?: p.split('/').takeIf { it.size == 2 }?.let { (a, b) ->
            val x = a.toDoubleOrNull()
            val y = b.toDoubleOrNull()
            if (x != null && y != null && y != 0.0) x / y else null
        }

        fun of(qty: Double, unit: String?, size: String?, food: Food? = null): Portion {
            var fuzzy = qty <= 0.0 || !qty.isFinite()
            val n = if (fuzzy) 1.0 else qty
            val given = unit?.takeIf { it.isNotBlank() }
            var u = unit(given)
            if (given != null && u == null) fuzzy = true
            when (u) {
                "g", "ml" -> return Portion(n, fuzzy)
                "kg", "l" -> return Portion(n * 1000, fuzzy)
            }
            val sz = size?.takeIf { it.isNotBlank() }
            if (sz != null && size(sz) == null) fuzzy = true
            u = u ?: food?.unit ?: "serving"
            val serve = food?.serve
            var base = serve?.get(u)
            if (base == null) {
                if (u != "tbsp" && u != "tsp") fuzzy = true
                val du = food?.unit.orEmpty()
                base = (if (u == "piece" || u == "serving") serve?.let { it["piece"] ?: it["serving"] ?: it[du] } else null) ?: dflt[u] ?: 100.0
            }
            val m = if (u == "tbsp" || u == "tsp") 1.0 else mult(sz)
            return Portion(n * base * m, fuzzy)
        }
    }
}
