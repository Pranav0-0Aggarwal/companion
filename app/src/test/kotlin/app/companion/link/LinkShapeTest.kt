package app.companion.link

import app.companion.core.Join
import app.companion.core.Json
import app.companion.core.Meeting
import app.companion.core.Via
import app.companion.data.Item
import app.companion.data.Task
import java.util.Locale
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LinkShapeTest {
    private val doc = Json.obj(checkNotNull(javaClass.getResourceAsStream("/link-messages.json")).use { it.readBytes().decodeToString() })
    private val zone = TimeZone.getDefault()
    private val locale = Locale.getDefault()
    private val t = 1_760_000_000_000

    @BeforeTest
    fun pin() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
    }

    @AfterTest
    fun unpin() {
        TimeZone.setDefault(zone)
        Locale.setDefault(locale)
    }

    @Suppress("UNCHECKED_CAST")
    private fun sec(k: String) = doc.getValue(k) as Map<String, Map<String, Any?>>

    private fun norm(o: Any?) = Json.parse(Json.write(o))

    private fun item(id: Long, kind: String, title: String, paise: Long = 0, state: String = "open", bank: String? = "HDFC Bank", last4: String? = "1234", block: (Item) -> Item = { it }) =
        block(Item(id = id, kind = kind, at = t, src = "sms", title = title, paise = paise, bank = bank, last4 = last4, state = state))

    private val otp = Item(id = 1, kind = "Otp", at = t, src = "sms", title = "HDFC Bank", code = "482913", expires = t + 600_000, state = "open")
    private val txn = item(5, "Debit", "Swiggy", 45_000) { it.copy(merchant = "Swiggy", mode = "UPI", category = "Food") }
    private val bill = item(7, "Bill", "HDFC credit card", 1_234_500, bank = "HDFC") { it.copy(minPaise = 50_000, due = 20_373) }
    private val paid = bill.copy(id = 8, state = "paid", due = 20_342)
    private val odd = item(9, "Debit", "XYZ Store", 499_900) { it.copy(merchant = "XYZ") }
    private val task = Task(id = 4, title = "Pay rent", due = 20_373, remindAt = 1_760_230_800_000)
    private val bare = Task(id = 6, title = "Call bank", done = true)
    private val meet = Meeting(1, "Standup", t + 4_000_000, t + 7_600_000, null, Join(Via.Meet, "https://meet.google.com/abc-defg-hij"), 1)

    @Test
    fun `shapes match the fixture`() {
        val s = sec("shape")
        assertEquals(s["otp"], norm(LinkShape.otp(otp)))
        assertEquals(s["txn"], norm(LinkShape.txn(txn)))
        assertEquals(s["bill"], norm(LinkShape.bill(bill)))
        assertEquals(s["paid"], norm(LinkShape.bill(paid)))
        assertEquals(s["alert"], norm(LinkShape.alert(odd)))
        assertEquals(s["alert_bill"], norm(LinkShape.due(bill)))
        assertEquals(s["hit"], norm(LinkShape.hit(txn)))
        assertEquals(s["task"], norm(LinkShape.task(task)))
        assertEquals(s["task_bare"], norm(LinkShape.task(bare)))
        assertEquals(s["meet"], norm(LinkShape.meet(meet)))
    }

    @Test
    fun `today matches the fixture`() {
        val today = LinkShape.today(
            "Good evening", 124_000, 4, 3_187_000, 5_000_000,
            listOf(LinkShape.otp(otp)), listOf(LinkShape.alert(odd), LinkShape.due(bill)), listOf(LinkShape.bill(bill)),
            listOf(LinkShape.task(task)), listOf(LinkShape.meet(meet)), listOf(LinkShape.txn(txn)),
        )
        assertEquals(sec("shape")["today"], norm(today))
    }

    @Test
    fun `events are the shape plus t`() {
        val ev = sec("ev")
        assertEquals(ev["otp"], norm(mapOf("t" to "otp") + LinkShape.otp(otp)))
        assertEquals(ev["alert"], norm(mapOf("t" to "alert") + LinkShape.alert(odd)))
        assertEquals(ev["alert_bill"], norm(mapOf("t" to "alert") + LinkShape.due(bill)))
    }

    @Test
    fun `a statement alert is a Bill alert that carries the bill id`() {
        val a = LinkShape.alert(bill.copy(kind = "Statement"))
        assertEquals("Bill", a["kind"])
        assertEquals(7L, a["bill"])
    }

    @Test
    fun `request params parse from the fixture`() {
        val r = sec("req")
        assertEquals(31, LinkArgs.days(r.getValue("ledger")))
        assertEquals(30, LinkArgs.days(r.getValue("today")))
        assertEquals(92, LinkArgs.days(mapOf("days" to 500L)))
        assertEquals(7L, LinkArgs.item(r.getValue("pay")))
        assertEquals(1L, LinkArgs.item(r.getValue("otp_drop")))
        assertEquals(4L, LinkArgs.item(r.getValue("task_done")))
        assertEquals("swiggy", LinkArgs.text(r.getValue("search"), "q", 200))
        assertEquals("Pay rent", LinkArgs.text(r.getValue("task_add"), "title", 200))
        assertEquals("6F1C2B8E-4D3A-4B7F-9E21-0A5C7D9E1F34", LinkArgs.text(r.getValue("task_add"), "key", 64))
        assertEquals("copied on the Mac", LinkArgs.text(r.getValue("clip"), "text", 20_000))
        assertEquals(1_760_230_800_000, LinkArgs.at(r.getValue("task_add")))
        assertEquals(null, LinkArgs.at(r.getValue("task_add_bare")))
    }

    @Test
    fun `oversize text is cut and blank or missing text is refused`() {
        assertEquals(200, LinkArgs.text(mapOf("title" to "x".repeat(500)), "title", 200).length)
        assertFailsWith<LinkBad> { LinkArgs.text(mapOf("title" to "  "), "title", 200) }
        assertFailsWith<LinkBad> { LinkArgs.text(emptyMap(), "title", 200) }
        assertFailsWith<LinkBad> { LinkArgs.item(mapOf("item" to "7")) }
    }
}
