package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteTest {
    private val reg = Registry(ToolSpecs.all.map { s -> object : Tool {
        override val name = s.name
        override val desc = s.desc
        override val args = s.args
        override suspend fun run(a: Map<String, Any?>) = ToolOut.Ok("")
    } })

    private fun call(text: String): Step.Call {
        val j = Route.of(text) ?: error("no route for $text")
        return assertIs<Step.Call>(reg.parse(j), j)
    }

    @Test
    fun commonQuestionsRouteToTheRightTool() {
        assertEquals("needs_you", call("What needs me today?").tool.name)
        assertEquals("needs_you", call("anything need my attention").tool.name)
        assertEquals("best_card", call("which card should I use").tool.name)
        assertEquals("trips", call("show my trips").tool.name)
        assertEquals("weight_trend", call("how is my weight going").tool.name)
        assertEquals("meetings", call("any meetings tomorrow").tool.name)
        assertEquals("tomorrow", call("any meetings tomorrow").args["day"])
        assertEquals("meals", call("what did I eat yesterday").tool.name)
        assertEquals("food_today", call("how many calories left today").tool.name)
    }

    @Test
    fun suggestionsCarryTheCapAndVeg() {
        val c = call("suggest a high protein vegetarian dinner under 600 calories")
        assertEquals("suggest_meal", c.tool.name)
        assertEquals(600.0, (c.args["kcal"] as Number).toDouble())
        assertEquals(true, c.args["veg"])
        assertNull(call("what should I eat for dinner, non veg is fine").args["veg"])
        assertEquals("suggest_meal", call("kya khau dinner mein").tool.name)
    }

    @Test
    fun comparisonsReadMerchantCategoryAndPeriods() {
        val s = call("Swiggy vs last month")
        assertEquals("compare", s.tool.name)
        assertEquals("Swiggy", s.args["merchant"])
        assertEquals("this month", s.args["period_a"])
        assertEquals("last month", s.args["period_b"])
        val f = call("food this month vs last month")
        assertEquals("food", f.args["category"])
        assertNull(f.args["merchant"])
        assertNull(call("this week vs last week").args["merchant"])
    }

    @Test
    fun logsAndMoneySumsAreLeftAlone() {
        assertNull(Route.of("lunch was 2 rotis, dal and a bowl of curd"))
        assertNull(Route.of("how much did I spend on food this month"))
        assertNull(Route.of("weighed 72.4 kg today"))
        assertNull(Route.of("hello"))
    }

    @Test
    fun requestsAreNotLogs() {
        assertTrue(Route.asks("suggest a dinner"))
        assertTrue(Route.asks("should I have paneer?"))
        assertFalse(Route.asks("had paneer for dinner"))
    }

    @Test
    fun plainMealStatementsBecomeItems() {
        val a = Route.meal("had 2 idli and chai for breakfast")!!
        assertEquals(listOf("idli" to 2.0, "chai" to 1.0), a.map { it.name to it.qty })
        assertNull(a[0].unit)
        val b = Route.meal("lunch was 2 rotis, dal and a bowl of curd")!!
        assertEquals(listOf("rotis", "dal", "curd"), b.map { it.name })
        assertEquals("bowl", b[2].unit)
        val c = Route.meal("kal raat 2 plate momos aur ek roll khaya")!!
        assertEquals(listOf("momos" to 2.0, "roll" to 1.0), c.map { it.name to it.qty })
        assertEquals("plate", c[0].unit)
        assertEquals(listOf("rotis", "paneer"), Route.meal("I had 3 rotis with paneer")!!.map { it.name })
        assertNull(Route.meal("suggest a dinner"))
        assertNull(Route.meal("had dinner?"))
        assertNull(Route.meal("how much did I spend on lunch"))
        assertNull(Route.meal("paid 300 for lunch"))
    }

    @Test
    fun prefilledLogTextLosesTheLeadWords() {
        assertEquals(listOf("idli" to 2.0), Route.items("I had 2 idli")!!.map { it.name to it.qty })
        assertEquals(listOf("roti", "dal", "dahi"), Route.items("2 roti, dal aur dahi")!!.map { it.name })
        assertTrue(Route.direct("What needs me today?"))
        assertTrue(Route.direct("had 2 idli and chai"))
        assertFalse(Route.direct("tell me a joke"))
    }
}
