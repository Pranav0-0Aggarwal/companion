package app.companion.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ValidatorTest {
    private val v = Validator(NOW, IST)
    private val a = LocalDate.of(2026, 9, 1)
    private val b = LocalDate.of(2026, 9, 30)

    @Test
    fun `good queries pass`() {
        assertNotNull(v.check(Query.SumSpend("food", "Swiggy", "4417", a, b)))
        assertNotNull(v.check(Query.ListBills(a, b, true)))
        assertNotNull(v.check(Query.TopMerchants(a, b, 5)))
    }

    @Test
    fun `categories are an enum`() {
        assertNull(v.check(Query.SumSpend("gold", null, null, a, b)))
        assertNull(v.check(Query.ListTxns("Food", null, null, a, b, 5)))
    }

    @Test
    fun `last four is exactly four digits`() {
        assertNull(v.check(Query.SumSpend(null, null, "123", a, b)))
        assertNull(v.check(Query.SumSpend(null, null, "12345", a, b)))
        assertNull(v.check(Query.SumSpend(null, null, "12a4", a, b)))
    }

    @Test
    fun `dates stay within three years back and one year ahead`() {
        assertNull(v.check(Query.SumSpend(null, null, null, LocalDate.of(2022, 1, 1), b)))
        assertNotNull(v.check(Query.SumSpend(null, null, null, LocalDate.of(2023, 10, 4), b)))
        assertNull(v.check(Query.SumSpend(null, null, null, a, LocalDate.of(2027, 10, 5))))
        assertNotNull(v.check(Query.SumSpend(null, null, null, a, LocalDate.of(2027, 10, 4))))
    }

    @Test
    fun `start must not follow end`() {
        assertNull(v.check(Query.SumSpend(null, null, null, b, a)))
        assertNull(v.check(Query.ListBills(b, a, true)))
    }

    @Test
    fun `limits are bounded`() {
        assertNull(v.check(Query.ListTxns(null, null, null, a, b, 0)))
        assertNull(v.check(Query.ListTxns(null, null, null, a, b, 51)))
        assertNull(v.check(Query.TopMerchants(a, b, 11)))
    }

    @Test
    fun `writes need sane titles and times`() {
        assertNull(v.check(Query.CreateReminder(" ", NOW + 1000)))
        assertNull(v.check(Query.CreateReminder("x".repeat(121), NOW + 1000)))
        assertNotNull(v.check(Query.CreateReminder("Pay rent", NOW + 1000)))
        assertNull(v.check(Query.CreateEvent("Dinner", NOW + 5000, NOW + 1000)))
        assertNull(v.check(Query.CreateEvent("Dinner", NOW, NOW + 3 * 24 * 3600_000L)))
    }

    @Test
    fun `tool call json is parsed and validated`() {
        val qs = v.parse(
            """[{"name":"sumSpend","arguments":{"category":"food","start":"2026-09-01","end":"2026-09-30"}},
                {"name":"sumSpend","arguments":{"category":"gold"}},
                {"name":"dropTable","arguments":{}},
                {"name":"createReminder","arguments":{"title":"Pay rent","at":"2026-10-05T09:00"}}]""",
        )
        assertEquals(2, qs.size)
        assertEquals(Query.SumSpend("food", null, null, a, b), qs[0])
        assertEquals(true, qs[1].write)
    }

    @Test
    fun `missing range defaults to the month and junk is dropped`() {
        val q = v.parse("""{"calls":[{"name":"listBills","arguments":{}}]}""").single() as Query.ListBills
        assertEquals(LocalDate.of(2026, 10, 1), q.start)
        assertEquals(LocalDate.of(2026, 11, 3), q.end)
        assertEquals(emptyList(), v.parse("not json"))
        assertEquals(emptyList(), v.parse("""[{"name":"sumSpend","arguments":{"start":"yesterday"}}]"""))
    }
}
