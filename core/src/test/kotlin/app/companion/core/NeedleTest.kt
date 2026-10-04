package app.companion.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NeedleTest {
    private val c = NeedleCalls(NOW, IST)

    private fun out(calls: String, ok: Boolean = true) = """{"type":"call","success":$ok,"function_calls":[$calls],"suppressed_calls":[],"confidence":0.4}"""

    private fun call(name: String, args: String) = """{"name":"$name","arguments":{$args}}"""

    @Test
    fun `tool schema parses and names the six queries`() {
        val tools = Json.parse(Needle.TOOLS) as List<*>
        val names = tools.map { ((it as Map<*, *>)["name"]) }
        assertEquals(listOf("sum_spend", "list_transactions", "list_bills", "top_merchants", "create_reminder", "create_event"), names)
        tools.forEach { t ->
            val p = (t as Map<*, *>)["parameters"] as Map<*, *>
            val props = p["properties"] as Map<*, *>
            val req = p["required"] as? List<*> ?: emptyList<Any>()
            assertTrue(props.keys.containsAll(req))
        }
    }

    @Test
    fun `system line carries today in kolkata`() {
        assertTrue(Needle.system(NOW, IST).startsWith("date: 2026-10-04 Sun; locale: en-IN; location: Asia/Kolkata; "))
    }

    @Test
    fun `sum spend resolves the period phrase`() {
        val q = assertIs<Query.SumSpend>(c.parse(out(call("sum_spend", """"category":"food","period":"last month""""))).single())
        assertEquals("food", q.category)
        assertEquals(LocalDate.of(2026, 9, 1), q.start)
        assertEquals(LocalDate.of(2026, 9, 30), q.end)
    }

    @Test
    fun `merchant card and limit pass through`() {
        val q = assertIs<Query.ListTxns>(c.parse(out(call("list_transactions", """"merchant":"Swiggy","card_last4":"4417","limit":7,"period":"this week""""))).single())
        assertEquals("Swiggy", q.merchant)
        assertEquals("4417", q.last4)
        assertEquals(7, q.limit)
    }

    @Test
    fun `list transactions without a period covers the last year`() {
        val q = assertIs<Query.ListTxns>(c.parse(out(call("list_transactions", """"limit":5"""))).single())
        assertEquals(LocalDate.of(2025, 10, 4), q.start)
        assertEquals(10, assertIs<Query.ListTxns>(c.parse(out(call("list_transactions", ""))).single()).limit)
    }

    @Test
    fun `bills and top merchants`() {
        val b = assertIs<Query.ListBills>(c.parse(out(call("list_bills", """"unpaid_only":true,"period":"this month""""))).single())
        assertEquals(true, b.unpaidOnly)
        assertEquals(LocalDate.of(2026, 10, 31), b.end)
        val t = assertIs<Query.TopMerchants>(c.parse(out(call("top_merchants", """"n":20,"period":"last year""""))).single())
        assertEquals(10, t.n)
    }

    @Test
    fun `reminder and event resolve the time phrase`() {
        val r = assertIs<Query.CreateReminder>(c.parse(out(call("create_reminder", """"title":"Pay rent","when":"tomorrow at 9am""""))).single())
        assertEquals("Pay rent", r.title)
        assertEquals(java.time.LocalDateTime.of(2026, 10, 5, 9, 0).atZone(IST).toInstant().toEpochMilli(), r.at)
        val e = assertIs<Query.CreateEvent>(c.parse(out(call("create_event", """"title":"Dinner","when":"tomorrow at 8pm""""))).single())
        assertEquals(3_600_000L, e.end - e.start)
    }

    @Test
    fun `no calls and failures give nothing`() {
        assertEquals(emptyList(), c.parse(out("")))
        assertEquals(emptyList(), c.parse(out(call("sum_spend", """"period":"last month""""), ok = false)))
        assertEquals(emptyList(), c.parse("not json"))
        assertEquals(emptyList(), c.parse("[]"))
    }

    @Test
    fun `anything invalid rejects the whole answer`() {
        val good = call("sum_spend", """"period":"last month"""")
        assertEquals(emptyList(), c.parse(out(call("sum_spend", """"category":"gold","period":"last month"""") + "," + good)))
        assertEquals(emptyList(), c.parse(out(call("sum_spend", """"period":"blue elephants""""))))
        assertEquals(emptyList(), c.parse(out(call("sum_spend", """"card_last4":"12","period":"last month""""))))
        assertEquals(emptyList(), c.parse(out(call("get_weather", """"period":"today""""))))
        assertEquals(emptyList(), c.parse(out(call("sum_spend", """"period":"1 Jan 2020 to 5 Jan 2020""""))))
        assertEquals(emptyList(), c.parse(out(call("create_reminder", """"title":"x","when":"never""""))))
        assertEquals(emptyList(), c.parse(out(List(4) { good }.joinToString(","))))
    }

    @Test
    fun `two valid calls both come back`() {
        val qs = c.parse(out(call("sum_spend", """"category":"food","period":"last month"""") + "," + call("create_reminder", """"title":"Pay rent","when":"tomorrow at 9am"""")))
        assertEquals(2, qs.size)
        assertTrue(qs[1].write)
    }
}
