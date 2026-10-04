package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CategoryTest {
    @Test
    fun `known merchants`() {
        assertEquals(Category.Food, Category.of("Swiggy"))
        assertEquals(Category.Transport, Category.of("Uber"))
        assertEquals(Category.Groceries, Category.of("BigBasket"))
        assertEquals(Category.Bills, Category.of("Airtel Xstream"))
        assertEquals(Category.Income, Category.of("Salary, Acme Labs", true))
        assertEquals(Category.Other, Category.of("Spotify In".replace("Spotify", "Zxqv")))
        assertEquals(Category.Other, Category.of(null))
    }
}
