package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResolverTest {
    private fun asset(n: String) = checkNotNull(javaClass.getResourceAsStream("/$n")).use { it.readBytes().decodeToString() }

    private val db = FoodDb.parse(asset("food_db.json"))

    private val menus = BrandMenus.parse(
        """{"version":1,"chains":[
        {"name":"Chaayos","aliases":["chayos"],"source":"synthetic","as_of":"2026-10-01","items":[
          {"name":"Masala Chai","size":"small","kcal":90,"protein":2,"carbs":12,"fat":3},
          {"name":"Masala Chai","size":"medium","kcal":130,"protein":3,"carbs":17,"fat":4},
          {"name":"Masala Chai","size":"large","kcal":180},
          {"name":"Samosa","kcal":250,"addons":[{"name":"extra chutney","kcal":20}]},
          {"name":"Veg Wrap","aliases":["veggie wrap"],"kcal":400,"addons":[{"name":"cheese","kcal":70}]}]},
        {"name":"Domino's","aliases":["dominos"],"source":"","as_of":"","items":[]}]}""",
    )

    private val r = Resolver(emptyMap(), db, menus)

    private fun hit(q: Req, res: Resolver = r) = assertIs<Hit>(res.resolve(q))

    @Test
    fun `db foods scale by grams and the default unit`() {
        val h = hit(Req("chapati", qty = 2.0))
        assertEquals("Roti", h.name)
        assertEquals(Origin.Db, h.source)
        assertEquals(232.0, h.kcal)
        assertEquals("piece", h.unit)
        assertEquals(2.0, h.qty)
        assertEquals(177.0, hit(Req("dal")).kcal)
    }

    @Test
    fun `macros scale with grams`() {
        val h = hit(Req("rice", qty = 200.0, unit = "g"))
        assertEquals(260.0, h.kcal)
        assertEquals(5.4, h.protein)
        assertEquals(56.0, h.carbs)
        assertEquals(0.6, h.fat)
        assertEquals("g", h.unit)
    }

    @Test
    fun `Hinglish aliases and typos match`() {
        assertEquals("Curd", hit(Req("dahi")).name)
        assertEquals("Milk", hit(Req("doodh")).name)
        assertEquals("Plain rice", hit(Req("chawal")).name)
        assertEquals("Dal tadka", hit(Req("daal")).name)
        assertEquals("Dal tadka", hit(Req("tadka dal")).name)
        assertEquals("Idli", hit(Req("idly")).name)
        assertEquals("Roti", hit(Req("rotis", qty = 3.0)).name)
        assertEquals("Boiled egg", hit(Req("anda")).name)
        assertEquals("Paneer butter masala", hit(Req("butter paneer")).name)
        assertEquals("Roti", hit(Req("the phulka")).name)
        assertEquals("Dal tadka", hit(Req("dall")).name)
        assertEquals("Masala dosa", hit(Req("masala dosai")).name)
    }

    @Test
    fun `stopwords are ignored`() {
        assertEquals("Dal tadka", hit(Req("a dal with")).name)
        assertEquals("Curd", hit(Req("ghar ka dahi")).name)
    }

    @Test
    fun `confidence follows the match quality`() {
        assertEquals(0.95, hit(Req("roti")).conf)
        assertTrue(hit(Req("dall")).conf in 0.6..0.9)
        assertTrue(hit(Req("butter masala")).conf in 0.6..0.9)
    }

    @Test
    fun `unknown items ask for calories with a category guess`() {
        val a = assertIs<Ask>(r.resolve(Req("ghee roast dosa")))
        assertEquals("I couldn't find ghee roast dosa. About how many calories, or should I estimate 350?", a.question)
        assertEquals(false, a.size)
        assertEquals("I couldn't find xyzzy foo. About how many calories, or should I estimate 300?", assertIs<Ask>(r.resolve(Req(" xyzzy foo "))).question)
        assertEquals(120, r.guess(Req("mango juice")))
        assertEquals(280, r.guess(Req("chocolate cake")))
        assertEquals(300, r.guess(Req("mystery")))
    }

    @Test
    fun `weak matches ask instead of guessing`() {
        assertIs<Ask>(r.resolve(Req("masala")))
        assertIs<Ask>(r.resolve(Req("paneer")))
        assertIs<Ask>(r.resolve(Req("")))
    }

    @Test
    fun `bowl and plate without a size ask for the size`() {
        val a = assertIs<Ask>(r.resolve(Req("dal", unit = "katori")))
        assertEquals("Small, medium or large bowl?", a.question)
        assertEquals(true, a.size)
        assertEquals("Small, medium or large plate?", assertIs<Ask>(r.resolve(Req("rice", unit = "plate"))).question)
        assertEquals(132.8, hit(a.req.copy(size = "small")).kcal)
        assertEquals(477.9, hit(Req("dal", qty = 2.0, unit = "bowl", size = "large")).kcal, 0.06)
    }

    @Test
    fun `grams and countable units never ask for a size`() {
        assertIs<Hit>(r.resolve(Req("dal", qty = 200.0, unit = "g")))
        assertIs<Hit>(r.resolve(Req("dal")))
        assertIs<Hit>(r.resolve(Req("roti", unit = "pcs")))
        assertIs<Hit>(r.resolve(Req("banana", unit = "bowl")))
    }

    @Test
    fun `fuzzy units cap confidence`() {
        val h = hit(Req("roti", unit = "handful"))
        assertEquals(116.0, h.kcal)
        assertEquals(0.75, h.conf)
    }

    @Test
    fun `mods adjust calories and cap confidence`() {
        assertEquals(420.0, hit(Req("burger", mods = listOf("extra cheese"))).kcal)
        assertEquals(270.0, hit(Req("burger", mods = listOf("no mayo"))).kcal)
        assertEquals(490.0, hit(Req("burger", mods = listOf("extra cheese", "add egg"))).kcal)
        assertEquals(0.75, hit(Req("burger", mods = listOf("extra cheese"))).conf)
        assertEquals(410.0, hit(Req("burger", mods = listOf("something odd"))).kcal)
        assertEquals(360.0, hit(Req("burger", mods = listOf("no onion"))).kcal)
        assertEquals(360.0, hit(Req("burger")).kcal)
    }

    @Test
    fun `brand items win over the db and are size aware`() {
        val big = hit(Req("Masala Chai", brand = "chaayos", size = "large"))
        assertEquals(Origin.Brand, big.source)
        assertEquals("Chaayos", big.brand)
        assertEquals(180.0, big.kcal)
        assertEquals(1.0, big.conf)
        assertEquals(90.0, hit(Req("masala chai", brand = "Chaayos", size = "small")).kcal)
        assertEquals(260.0, hit(Req("masala chai", brand = "Chaayos", qty = 2.0)).kcal)
        assertEquals(3.0, hit(Req("masala chai", brand = "Chaayos", size = "medium")).protein)
        assertEquals(0.8, hit(Req("masala chai", brand = "Chaayos")).conf)
    }

    @Test
    fun `an unsized brand item scales by the size multiplier`() {
        val h = hit(Req("samosa", brand = "Chaayos", size = "large", qty = 2.0))
        assertEquals(675.0, h.kcal)
        assertEquals(0.8, h.conf)
        assertEquals(500.0, hit(Req("samosa", brand = "Chaayos", qty = 2.0)).kcal)
    }

    @Test
    fun `the chain is found in the item name`() {
        val h = hit(Req("Chayos masala chai"))
        assertEquals("Chaayos", h.brand)
        assertEquals(Origin.Brand, h.source)
        assertEquals("Chaayos", hit(Req("veggie wrap from chaayos")).brand)
        assertEquals(400.0, hit(Req("veggie wrap from chaayos")).kcal)
    }

    @Test
    fun `brand addons win over the table`() {
        assertEquals(470.0, hit(Req("veg wrap", brand = "Chaayos", mods = listOf("extra cheese"))).kcal)
        assertEquals(330.0, hit(Req("veg wrap", brand = "Chaayos", mods = listOf("no cheese"))).kcal)
        assertEquals(270.0, hit(Req("samosa", brand = "Chaayos", mods = listOf("extra chutney"))).kcal)
        assertEquals(0.75, hit(Req("samosa", brand = "Chaayos", mods = listOf("extra chutney"))).conf)
    }

    @Test
    fun `a brand without menu items falls back to the db`() {
        val h = hit(Req("dal tadka", brand = "Domino's"))
        assertEquals(Origin.Db, h.source)
        assertEquals("Domino's", h.brand)
        assertEquals(Origin.Db, hit(Req("pizza", brand = "dominos", unit = "slice")).source)
        assertIs<Ask>(r.resolve(Req("ghee roast dosa", brand = "Chaayos")))
    }

    @Test
    fun `answers become skus that are never asked twice`() {
        val q = Req("Ghee Roast Dosa!")
        assertIs<Ask>(r.resolve(q))
        val sku = r.asked(q, 350.0, protein = 8.0)
        assertEquals("|ghee roast dosa", sku.key)
        assertEquals("serving", sku.per)
        assertEquals(8.0, sku.protein)
        assertNull(sku.carbs)
        val again = Resolver(mapOf(sku.key to sku), db, menus)
        val h = hit(Req("ghee roast dosa", qty = 2.0), again)
        assertEquals(Origin.Sku, h.source)
        assertEquals(1.0, h.conf)
        assertEquals(700.0, h.kcal)
        assertEquals(16.0, h.protein)
        assertEquals("Ghee Roast Dosa!", h.name)
    }

    @Test
    fun `skus match with the brand then without it`() {
        val a = Sku(Sku.key("Chaayos", "Special Chai"), "Chaayos", "Special Chai", 111.0, null, null, null)
        val b = Sku(Sku.key(null, "Special Chai"), null, "Special Chai", 222.0, null, null, null)
        val both = Resolver(mapOf(a.key to a, b.key to b), db, menus)
        assertEquals(111.0, hit(Req("special chai", brand = "chayos"), both).kcal)
        assertEquals(222.0, hit(Req("special chai"), both).kcal)
        assertEquals(222.0, hit(Req("special chai", brand = "Other"), both).kcal)
        assertEquals(111.0, hit(Req("Chaayos special chai"), both).kcal)
    }

    @Test
    fun `sku keys fold case punctuation and accents`() {
        assertEquals("mcdonalds|mcaloo tikki", Sku.key("McDonald's", "  McAloo-Tikki!! "))
        assertEquals("|cafe latte", Sku.key(null, "Café  Latte"))
        assertEquals(Sku.key("KFC", "zinger"), Sku.key("kfc", "ZINGER."))
    }

    @Test
    fun `an answered estimate is marked low confidence`() {
        val q = Req("mystery stew", qty = 2.0, mods = listOf("extra cheese"))
        val h = r.answer(q, 300.0, estimated = true)
        assertEquals(Origin.Estimate, h.source)
        assertEquals(0.5, h.conf)
        assertEquals(600.0, h.kcal)
        val a = r.answer(q, 410.0, 10.0, 20.0, 5.0)
        assertEquals(Origin.Asked, a.source)
        assertEquals(1.0, a.conf)
        assertEquals(820.0, a.kcal)
        assertEquals(20.0, a.protein)
    }

    @Test
    fun `origin labels round trip`() {
        Origin.entries.forEach { assertEquals(it, Origin.of(it.label)) }
        assertEquals(listOf("sku", "brand", "db", "asked", "estimate"), Origin.entries.map { it.label })
        assertNull(Origin.of("nope"))
    }

    @Test
    fun runTogetherFoodsSplitIntoKnownItems() {
        val parts = r.parts(Req("roti dal", qty = 2.0))!!
        assertEquals(listOf("roti", "dal"), parts.map { it.name })
        assertEquals(2.0, parts[0].qty)
        assertEquals(null, r.parts(Req("roti")))
        assertEquals(null, r.parts(Req("mystery stew")))
    }
}
