package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrderLinesTest {
    private fun one(line: String) = OrderLines.parse(line).single()

    @Test
    fun `quantity before the name`() {
        assertEquals(Req("Chicken Burger", qty = 1.0), one("1 x Chicken Burger"))
        assertEquals(Req("Masala Dosa", qty = 2.0), one("2 × Masala Dosa  ₹240"))
        assertEquals(Req("Masala Dosa", qty = 3.0), one("3x Masala Dosa"))
        assertEquals(Req("Masala Dosa", qty = 2.0), one("- 2 * Masala Dosa"))
        assertEquals(Req("Veg Biryani", qty = 2.0, size = "large"), one("Qty: 2  Veg Biryani (Large)"))
    }

    @Test
    fun `quantity after the name`() {
        assertEquals(Req("Paneer Butter Masala", qty = 2.0), one("Paneer Butter Masala x 2"))
        assertEquals(Req("Butter Naan", qty = 2.0), one("Butter Naan (2)"))
        assertEquals(Req("Butter Naan", qty = 4.0), one("Butter Naan (Qty: 4)"))
        assertEquals(Req("Chicken 65", qty = 2.0), one("Chicken 65 x2"))
    }

    @Test
    fun `prices are dropped`() {
        assertEquals(Req("Veg Puff", qty = 2.0), one("2 x Veg Puff Rs. 80.00"))
        assertEquals(Req("Veg Puff", qty = 2.0), one("2 x Veg Puff - INR 80"))
        assertEquals(Req("Veg Puff", qty = 2.0), one("2 x Veg Puff ₹1,080.50/-"))
        assertEquals(Req("Veg Puff", qty = 1.0), one("Veg Puff x 1   ₹ 80"))
    }

    @Test
    fun `sizes and mods`() {
        assertEquals(Req("Chicken Burger", mods = listOf("extra cheese")), one("Chicken Burger + Extra Cheese"))
        assertEquals(Req("Paneer Wrap", qty = 1.0, mods = listOf("with extra cheese")), one("1 x Paneer Wrap with extra cheese - Rs. 180"))
        assertEquals(Req("Veg Burger", mods = listOf("no mayo", "no onion")), one("Order\nVeg Burger No Mayo No Onion"))
        assertEquals(Req("Veg Burger", mods = listOf("add egg")), one("Veg Burger + Egg"))
        assertEquals(Req("Veg Burger", size = "medium", mods = listOf("extra cheese")), one("Veg Burger (Medium, Extra Cheese)"))
        assertEquals(Req("Veg Whopper Meal", size = "medium", mods = listOf("with fries")), one("Veg Whopper Meal - Medium Fries"))
        assertEquals(Req("Cold Coffee", size = "large", qty = 2.0), one("2 x Cold Coffee (Large, 500 ml)"))
        assertEquals(Req("Latte", size = "small"), one("Latte - Small"))
        assertEquals(Req("Veg Biryani", mods = listOf("with raita")), one("1 x Veg Biryani w/ raita"))
    }

    @Test
    fun `a full order message keeps only the items`() {
        val text = """
            Your order from Test Kitchen is confirmed!
            Order #48213
            Item        Qty   Price
            1 x Chicken Burger        ₹199
            2 × Masala Dosa           ₹240
            Butter Naan (2)           ₹90
            Item total                ₹529
            Delivery fee              ₹30
            GST and packaging         ₹26.45
            Discount                  -₹50
            Total paid                ₹535.45
            Delivered to: Flat 4, Sample Nagar, 560001
            Paid via UPI
            Thank you for ordering!
        """.trimIndent()
        assertEquals(listOf(Req("Chicken Burger"), Req("Masala Dosa", qty = 2.0), Req("Butter Naan", qty = 2.0)), OrderLines.parse(text))
    }

    @Test
    fun `plain item lines in an order count`() {
        val text = "Order summary\nVeg Whopper Meal - Medium Fries\nChicken Burger + Extra Cheese\nTotal Rs. 400"
        assertEquals(
            listOf(Req("Veg Whopper Meal", size = "medium", mods = listOf("with fries")), Req("Chicken Burger", mods = listOf("extra cheese"))),
            OrderLines.parse(text),
        )
    }

    @Test
    fun `non order text gives nothing`() {
        assertEquals(emptyList(), OrderLines.parse(""))
        assertEquals(emptyList(), OrderLines.parse("Hello,\nLet's meet at 5pm tomorrow. Bring 2 laptops.\nThanks"))
        assertEquals(emptyList(), OrderLines.parse("Your OTP is 123456. Do not share."))
        assertEquals(emptyList(), OrderLines.parse("Total: Rs. 500\nDelivery fee 30\nGST 12"))
        assertEquals(emptyList(), OrderLines.parse("3 apples\nGood Morning Team"))
        assertEquals(emptyList(), OrderLines.parse("Your order #123 has been shipped and will arrive soon."))
    }

    @Test
    fun `the brand hint is carried and items are capped`() {
        assertEquals(Req("Chicken Burger", brand = "KFC"), OrderLines.parse("1 x Chicken Burger", "KFC").single())
        val many = (1..30).joinToString("\n") { "1 x Item Number $it" }
        val out = OrderLines.parse(many)
        assertEquals(20, out.size)
        assertEquals("Item Number 1", out.first().name)
    }

    @Test
    fun `names are limited to sixty characters`() {
        assertTrue(OrderLines.parse("1 x ${"A".repeat(61)}").isEmpty())
        assertEquals(60, one("1 x ${"A".repeat(60)}").name.length)
        assertTrue(OrderLines.parse("1 x " + "word ".repeat(40)).isEmpty())
    }

    @Test
    fun `odd quantities and bullets`() {
        assertTrue(OrderLines.parse("99 x Samosa").isEmpty())
        assertEquals(Req("Samosa", qty = 2.0), one("• 2 x Samosa"))
        assertEquals(Req("Samosa", qty = 2.0), one("1. 2 x Samosa"))
        assertEquals(Req("Samosa", qty = 2.0), one("  2   x   Samosa  "))
    }
}
