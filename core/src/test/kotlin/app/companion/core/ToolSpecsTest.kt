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
            assertTrue(l.lines().any { x -> x.startsWith(it.name + "(") }, it.name)
        }
    }

    @Test
    fun systemPromptStaysWithinItsBudget() {
        val p = Prompt.system(reg.listing())
        assertTrue(p.length <= 4200, "${p.length} chars")
        assertTrue(reg.listing().lines().all { it.substringAfterLast("): ").split(' ').size <= 8 })
        assertTrue(Regex("^.* -> \\{", RegexOption.MULTILINE).findAll(p).count() <= 3)
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
    fun readCalls() {
        assertIs<Step.Call>(step("""{"tool":"search_messages","args":{"q":"refund","from":"Amazon","days":30}}"""))
        assertIs<Step.Call>(step("""{"tool":"ledger","args":{"merchant":"Swiggy","category":"food","card":"HDFC","min":100,"max":2500.5,"period":"last month"}}"""))
        assertIs<Step.Call>(step("""{"tool":"ledger","args":{}}"""))
        assertIs<Step.Call>(step("""{"tool":"top_merchants","args":{"period":"this month","n":5}}"""))
        assertIs<Step.Call>(step("""{"tool":"compare","args":{"period_a":"this month","period_b":"last month","merchant":"Swiggy"}}"""))
        assertIs<Step.Bad>(step("""{"tool":"compare","args":{"period_a":"this month"}}"""))
        assertIs<Step.Call>(step("""{"tool":"needs_you","args":{"n":5}}"""))
        assertIs<Step.Call>(step("""{"tool":"bill_cycle","args":{"card":"ICICI"}}"""))
        assertIs<Step.Call>(step("""{"tool":"best_card","args":{}}"""))
        assertIs<Step.Call>(step("""{"tool":"meals","args":{"date":"yesterday"}}"""))
        assertIs<Step.Call>(step("""{"tool":"weight_trend","args":{"weeks":12}}"""))
        assertIs<Step.Bad>(step("""{"tool":"weight_trend","args":{"weeks":"many"}}"""))
        assertIs<Step.Call>(step("""{"tool":"meetings","args":{"day":"tomorrow"}}"""))
        assertIs<Step.Call>(step("""{"tool":"trips","args":{}}"""))
    }

    @Test
    fun actCalls() {
        assertIs<Step.Call>(step("""{"tool":"mark_paid","args":{"bill":"i:123"}}"""))
        assertIs<Step.Bad>(step("""{"tool":"mark_paid","args":{}}"""))
        assertIs<Step.Call>(step("""{"tool":"file","args":{"item":"i:5","category":"food"}}"""))
        assertIs<Step.Bad>(step("""{"tool":"file","args":{"item":"i:5","category":"dining"}}"""))
        assertIs<Step.Call>(step("""{"tool":"retype","args":{"item":"i:5","label":"promo"}}"""))
        assertIs<Step.Bad>(step("""{"tool":"retype","args":{"item":"i:5","label":"otp"}}"""))
        assertIs<Step.Call>(step("""{"tool":"mark_dup","args":{"item":"i:5","keep":"i:4"}}"""))
        assertIs<Step.Call>(step("""{"tool":"rename_merchant","args":{"from":"SWIGGY LTD","to":"Swiggy"}}"""))
        assertIs<Step.Call>(step("""{"tool":"not_spending","args":{"merchant":"Zerodha"}}"""))
        assertIs<Step.Call>(step("""{"tool":"add_card","args":{"bank":"HDFC","last4":"1234"}}"""))
        assertIs<Step.Call>(step("""{"tool":"set_budget","args":{"amount":50000}}"""))
        assertIs<Step.Bad>(step("""{"tool":"set_budget","args":{"amount":"lots"}}"""))
        assertIs<Step.Call>(step("""{"tool":"dismiss","args":{"item":"i:9"}}"""))
    }

    @Test
    fun newSpecsStayCompact() {
        val new = ToolSpecs.all.drop(16)
        assertEquals(21, new.size)
        assertTrue(new.all { it.desc.split(' ').size <= 8 }, "desc")
        assertTrue(new.all { t -> t.args.all { it.desc.split(' ').filter(String::isNotEmpty).size <= 6 } }, "arg desc")
    }

    @Test
    fun finalAnswersAreSay() {
        assertEquals(Step.Say("You spent 2,400."), step("""{"say":"You spent 2,400."}"""))
    }
}
