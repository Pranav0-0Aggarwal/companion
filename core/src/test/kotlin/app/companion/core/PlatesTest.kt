package app.companion.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class PlatesTest {
    private val db = FoodDb.parse(File("../app/src/main/assets/food_db.json").readText())

    @Test
    fun vegPlatesStayUnderTheCapWithTheMostProteinFirst() {
        val p = Plates.of(db, 600, veg = true)
        assertTrue(p.isNotEmpty())
        assertTrue(p.all { it.kcal <= 600 })
        assertTrue(p.none { pl -> pl.items.any { it.first.contains("Chicken") || it.first.contains("Egg") || it.first.contains("Omelette") } })
        assertTrue(p.zipWithNext().all { (a, b) -> a.protein >= b.protein })
        assertTrue(p.map { it.items.first() }.toSet().size == p.size)
    }

    @Test
    fun aTinyCapGivesNothing() {
        assertTrue(Plates.of(db, 50, veg = false).isEmpty())
    }
}
