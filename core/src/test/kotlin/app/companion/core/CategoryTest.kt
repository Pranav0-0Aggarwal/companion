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

    @Test
    fun `indian merchant words`() {
        assertEquals(Category.Food, Category.of("Boba Bistro"))
        assertEquals(Category.Food, Category.of("Blinkit Foods Limited"))
        assertEquals(Category.Groceries, Category.of("Charvi Farm Fresh"))
        assertEquals(Category.Shopping, Category.of("Amazon Pay In U"))
        assertEquals(Category.Shopping, Category.of("Sharma Enterprises"))
        assertEquals(Category.Travel, Category.of("Ibibo Group Private Limited"))
        assertEquals(Category.Transfer, Category.of("CRED Club"))
        assertEquals(Category.Bills, Category.of("PPBBPSUTILITIES"))
        assertEquals(Category.Health, Category.of("City Hospital"))
    }

    private fun debit(m: String?, mode: Mode) = Event.Debit(100, "INR", null, null, m, mode)

    @Test
    fun `unknown payee over upi or a bank is a transfer`() {
        assertEquals(Category.Transfer, Category.of(debit("Rohan M", Mode.Upi)))
        assertEquals(Category.Transfer, Category.of(debit(null, Mode.Netbanking)))
        assertEquals(Category.Other, Category.of(debit("Rohan M", Mode.Card)))
        assertEquals(Category.Other, Category.of(Event.CardSpend(100, "INR", null, null, "Zxqv")))
    }

    @Test
    fun `known payee keeps its category and credits default to income`() {
        assertEquals(Category.Food, Category.of(debit("Swiggy", Mode.Upi)))
        assertEquals(Category.Income, Category.of(Event.Credit(100, "INR", null, null, "Rohan M", Mode.Upi)))
    }
}
