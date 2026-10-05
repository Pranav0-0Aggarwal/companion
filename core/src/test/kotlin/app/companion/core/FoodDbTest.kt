package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FoodDbTest {
    private fun asset(n: String) = checkNotNull(javaClass.getResourceAsStream("/$n")).use { it.readBytes().decodeToString() }

    private val db = FoodDb.parse(asset("food_db.json"))

    private fun food(extra: String = "") =
        """{"version":1,"source":"s","foods":[{"id":"a","name":"A","per100":{"kcal":100,"protein":1,"carbs":2,"fat":3},"serve":{"bowl":150},"unit":"bowl","source":"seed-estimate"$extra}]}"""

    @Test
    fun `the bundled food db is seeded and sane`() {
        assertTrue(db.foods.size >= 25)
        assertEquals(db.foods.size, db.foods.map { it.id }.toSet().size)
        db.foods.forEach {
            assertEquals("seed-estimate", it.source)
            assertTrue(it.kcal in 0.0..900.0)
            assertTrue(it.protein >= 0 && it.carbs >= 0 && it.fat >= 0)
            assertTrue(it.protein + it.carbs + it.fat <= 100.0, it.id)
            assertTrue(it.unit in it.serve && it.serve.values.all { g -> g > 0 })
            assertTrue(it.name.isNotBlank() && it.aliases.all(String::isNotBlank))
        }
    }

    @Test
    fun `the seed covers the common dishes and Hinglish names`() {
        val names = db.foods.flatMap { listOf(it.name.lowercase()) + it.aliases }.toSet()
        listOf("roti", "chapati", "phulka", "dal", "daal", "sabzi", "chawal", "anda", "dahi", "doodh", "chai", "idli", "maggi").forEach { assertTrue(it in names, it) }
    }

    @Test
    fun `the bundled brand menus list every chain once`() {
        val menus = BrandMenus.parse(asset("brand_menus.json"))
        val want = listOf("California Burrito", "McDonald's", "Domino's", "KFC", "Subway", "Burger King", "Starbucks", "Taco Bell", "Chaayos", "Third Wave", "Box8", "Faasos")
        assertEquals(want.sorted(), menus.chains.map { it.name }.sorted())
        assertTrue(menus.chains.all { it.items.isEmpty() })
        assertEquals("Domino's", menus.chain("dominos")?.name)
        assertEquals("California Burrito", menus.chain("cali burrito")?.name)
    }

    @Test
    fun `a minimal food parses`() {
        val f = FoodDb.parse(food(""","aliases":["x"]""")).foods.single()
        assertEquals(listOf("x"), f.aliases)
        assertEquals(150.0, f.serve["bowl"])
    }

    @Test
    fun `bad foods are rejected without echoing content`() {
        val bad = listOf(
            "{}", """{"version":2,"source":"s","foods":[]}""", """{"version":1,"foods":[]}""", """{"version":1,"source":"s","foods":{}}""",
            food().replace("\"id\":\"a\"", "\"id\":\"\""), food().replace("\"kcal\":100", "\"kcal\":-1"), food().replace("\"kcal\":100", "\"kcal\":901"),
            food().replace("\"kcal\":100", "\"kcal\":\"x\""), food().replace("\"fat\":3", "\"fat\":-3"), food().replace("\"unit\":\"bowl\"", "\"unit\":\"cup\""),
            food().replace("\"bowl\":150", "\"bowl\":0"), food().replace("\"bowl\":150", "\"jug\":150").replace("\"unit\":\"bowl\"", "\"unit\":\"jug\""),
            food().replace(",\"source\":\"seed-estimate\"", ""), food(""","aliases":"x""""), food(""","aliases":[1]"""),
            """{"version":1,"source":"s","foods":[${food().substringAfter("[").substringBeforeLast("]")},${food().substringAfter("[").substringBeforeLast("]")}]}""",
        )
        bad.forEach {
            val e = assertFailsWith<Exception>(it) { FoodDb.parse(it) }
            assertTrue(e.message.orEmpty().length < 40, it)
        }
    }

    private fun menu(items: String = "[]", source: String = "", asOf: String = "") =
        """{"version":1,"chains":[{"name":"Test Cafe","aliases":["tc"],"source":"$source","as_of":"$asOf","items":$items}]}"""

    @Test
    fun `menus with items parse and need a source`() {
        val items = """[{"name":"Latte","size":"regular","kcal":150,"protein":8,"addons":[{"name":"extra shot","kcal":5}],"serve":300},{"name":"Cake","kcal":400}]"""
        val c = BrandMenus.parse(menu(items, "published", "2026-10-01")).chains.single()
        assertEquals("medium", c.items[0].size)
        assertEquals(8.0, c.items[0].protein)
        assertEquals(listOf(Addon("extra shot", 5.0)), c.items[0].addons)
        assertEquals(null, c.items[1].size)
        assertFailsWith<Exception> { BrandMenus.parse(menu(items)) }
    }

    @Test
    fun `bad menus are rejected`() {
        listOf(
            "{}", """{"version":1,"chains":[{"name":"","source":"","as_of":"","items":[]}]}""", """{"version":1,"chains":[{"name":"A","as_of":"","items":[]}]}""",
            menu("""[{"name":"x"}]""", "s", "d"), menu("""[{"name":"x","kcal":-1}]""", "s", "d"), menu("""[{"name":"x","kcal":1,"size":"huge"}]""", "s", "d"),
            menu("""[{"name":"x","kcal":1,"addons":[{"name":"y"}]}]""", "s", "d"), menu("""[{"kcal":1}]""", "s", "d"),
            """{"version":1,"chains":[{"name":"A","source":"","as_of":"","items":[]},{"name":"a","source":"","as_of":"","items":[]}]}""",
        ).forEach { assertFailsWith<Exception>(it) { BrandMenus.parse(it) } }
    }

    @Test
    fun `chain names are found inside item text and stripped`() {
        val m = BrandMenus.parse(asset("brand_menus.json"))
        val (c, rest) = assertNotNull(m.match("Zinger burger from KFC"))
        assertEquals("KFC", c.name)
        assertEquals("zinger burger from", rest)
        assertEquals("McDonald's", m.match("mcdonalds fries")?.first?.name)
        assertEquals(null, m.match("kfcx fries"))
    }
}
