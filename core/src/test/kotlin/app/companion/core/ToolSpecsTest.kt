package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class Stub(s: ToolSpec) : Tool {
    override val name = s.name
    override val desc = s.desc
    override val args = s.args

    override suspend fun run(a: Map<String, Any?>): ToolOut = ToolOut.Ok("")
}

class ToolSpecsTest {
    private val reg = Registry(ToolSpecs.all.map(::Stub))

    private fun step(s: String) = reg.parse(s)

    @Test
    fun namesAreUniqueSnakeCase() {
        val n = ToolSpecs.all.map { it.name }
        assertEquals(n.size, n.toSet().size)
        assertTrue(n.all { Regex("[a-z]+(_[a-z]+)*").matches(it) })
    }

    @Test
    fun grammarAndListingCoverEveryTool() {
        val g = reg.grammar()
        val l = reg.listing()
        ToolSpecs.all.forEach {
            assertTrue("\\\"${it.name}\\\"" in g, it.name)
            assertTrue("\"${it.name}\"" in l, it.name)
        }
    }

    @Test
    fun moneyCalls() {
        assertIs<Step.Call>(step("""{"tool":"spend","args":{"period":"last month","category":"food","compare":"the month before"}}"""))
        assertIs<Step.Call>(step("""{"tool":"bills","args":{"range":"this week"}}"""))
        assertIs<Step.Call>(step("""{"tool":"cards","args":{}}"""))
        assertIs<Step.Bad>(step("""{"tool":"spend","args":{"category":"food"}}"""))
        assertIs<Step.Bad>(step("""{"tool":"spend","args":{"period":"today","category":"snacks"}}"""))
    }

    @Test
    fun foodCalls() {
        val c = step("""{"tool":"log_meal","args":{"items":[{"name":"roti","qty":2},{"name":"dal","unit":"bowl","size":"small","mods":["extra ghee"]}],"meal":"lunch","when":"yesterday"}}""")
        assertIs<Step.Call>(c)
        assertEquals(2, (c.args["items"] as List<*>).size)
        assertIs<Step.Bad>(step("""{"tool":"log_meal","args":{"items":[{"qty":2}]}}"""))
        assertIs<Step.Bad>(step("""{"tool":"log_meal","args":{"items":[{"name":"roti"}],"meal":"brunch"}}"""))
        assertIs<Step.Call>(step("""{"tool":"log_weight","args":{"kg":72.4}}"""))
        assertIs<Step.Call>(step("""{"tool":"set_kcal","args":{"item_key":"ghee roast dosa","kcal":350}}"""))
    }

    @Test
    fun tripVaultAndGeneralCalls() {
        assertIs<Step.Call>(step("""{"tool":"start_trip","args":{"name":"Goa","from":"tomorrow","to":"next sunday"}}"""))
        assertIs<Step.Call>(step("""{"tool":"vault_add","args":{"kind":"Insurance","number":"P123456","expires":"2027-03-01"}}"""))
        assertIs<Step.Bad>(step("""{"tool":"vault_add","args":{"kind":"Passport"}}"""))
        assertIs<Step.Call>(step("""{"tool":"remind","args":{"text":"pay rent","when":"5th at 9am"}}"""))
        assertIs<Step.Call>(step("""{"tool":"open","args":{"screen":"bills"}}"""))
        assertIs<Step.Call>(step("""{"tool":"open","args":{"screen":"vault"}}"""))
        assertIs<Step.Bad>(step("""{"tool":"open","args":{"screen":"nowhere"}}"""))
    }

    @Test
    fun finalAnswersAreSay() {
        assertEquals(Step.Say("You spent 2,400."), step("""{"say":"You spent 2,400."}"""))
    }
}
