package app.companion.link

import app.companion.core.Meeting
import app.companion.data.Item
import app.companion.data.Task
import app.companion.data.bill
import app.companion.data.dueDate
import app.companion.data.money
import app.companion.ui.clock
import app.companion.ui.money
import app.companion.ui.shortDay
import app.companion.ui.zone

typealias Obj = Map<String, Any?>

object LinkShape {
    private fun day(d: Long?) = d?.let { java.time.LocalDate.ofEpochDay(it).atStartOfDay(zone()).toInstant().toEpochMilli() }

    private fun card(i: Item) = listOfNotNull(i.bank?.removeSuffix(" Bank"), i.last4?.let { "•$it" }).joinToString(" ").ifEmpty { null }

    fun otp(i: Item): Obj = mapOf("id" to i.id, "code" to i.code, "from" to i.title, "at" to i.at, "expires" to i.expires)

    fun txn(i: Item): Obj = mapOf(
        "id" to i.id, "kind" to i.kind, "title" to i.title, "merchant" to i.merchant, "paise" to i.paise, "cur" to i.currency,
        "at" to i.at, "category" to i.category, "card" to card(i), "mode" to i.mode,
    )

    fun bill(i: Item): Obj = mapOf(
        "id" to i.id, "title" to i.title, "bank" to i.bank, "last4" to i.last4, "paise" to i.paise, "min" to i.minPaise,
        "cur" to i.currency, "due" to day(i.due), "state" to if (i.state == "paid") "paid" else "open",
    )

    fun alert(i: Item): Obj {
        val odd = i.money && i.paise > 0
        return (if (i.bill) mapOf("bill" to i.id) else emptyMap()) + mapOf(
            "id" to i.id, "kind" to i.kind,
            "title" to if (odd) "Odd charge: ${money(i.paise, i.currency)} at ${i.merchant ?: i.title}" else i.title,
            "sub" to listOfNotNull(card(i), clock(i.at)).joinToString(" · "), "paise" to i.paise, "at" to i.at,
        )
    }

    fun due(i: Item): Obj = mapOf(
        "id" to i.id, "bill" to i.id, "kind" to "Bill", "title" to i.title,
        "sub" to listOfNotNull(i.dueDate?.let { "due ${shortDay(it)}" }, card(i)).joinToString(" · "), "paise" to i.paise, "at" to i.at,
    )

    fun hit(i: Item): Obj = mapOf("id" to i.id, "kind" to i.kind, "title" to i.title, "sub" to listOfNotNull(card(i), clock(i.at)).joinToString(" · "), "paise" to i.paise, "at" to i.at)

    fun task(t: Task): Obj = mapOf("id" to t.id, "title" to t.title, "due" to day(t.due), "remindAt" to t.remindAt, "done" to t.done)

    fun meet(m: Meeting): Obj = mapOf("title" to m.title, "start" to m.start, "end" to m.end, "link" to m.join?.url)
}
