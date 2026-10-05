package app.companion.data

import app.companion.core.Body
import app.companion.core.CardPay
import app.companion.core.Category
import app.companion.core.Event
import app.companion.core.Filed
import app.companion.core.Fingerprint
import app.companion.core.Group
import app.companion.core.Labels
import app.companion.core.Mode
import app.companion.core.Raw
import app.companion.core.Slip
import app.companion.core.Stage
import app.companion.core.Suggest
import app.companion.core.Suggestion
import app.companion.core.Verdict
import java.time.LocalDate
import java.time.ZoneId

val Item.credit get() = kind == "Credit"
val Item.money get() = kind == "Debit" || kind == "Credit" || kind == "CardSpend"
val Item.bill get() = kind == "Bill" || kind == "Statement"
val Item.dueDate: LocalDate? get() = due?.let(LocalDate::ofEpochDay)
val Item.tagList get() = tags?.split(',')?.filter { it.isNotEmpty() }.orEmpty()
fun Item.slip() = Slip(at, due, last4, merchant ?: bank)
fun Item.filed() = Filed(kind, tags, category, state)

fun Item.move(): Event.Move? {
    val m = Mode.entries.firstOrNull { it.name == mode } ?: Mode.Other
    return when (kind) {
        "Debit" -> Event.Debit(paise, currency, last4, bank, merchant, m)
        "Credit" -> Event.Credit(paise, currency, last4, bank, merchant, m)
        "CardSpend" -> Event.CardSpend(paise, currency, last4, bank, merchant)
        else -> null
    }
}

val Item.cardPay get() = CardPay.of(kind, category, merchant ?: title)
val Item.brand get() = if (money || bill || kind == "Delivery") merchant ?: bank else null
val Item.moved get() = flow != null || cardPay

fun Item.cal(): Suggestion.Cal? {
    val s = start ?: return null
    val e = end ?: return null
    return Suggestion.Cal(if (kind == "Personal") "Plan with $title" else title, s, e, e - s == 86_400_000L, if (kind == "Travel") note else "")
}

fun Item.fp(): Fingerprint? {
    val g = when (kind) {
        "Debit", "CardSpend" -> Group.Out
        "Credit" -> Group.In
        "Bill", "Statement" -> Group.Due
        "Otp" -> Group.Code
        else -> return null
    }
    return Fingerprint(g, paise, last4, Fingerprint.norm(merchant), dueDate, code)
}

fun Fingerprint.kinds() = when (group) {
    Group.Out -> listOf("Debit", "CardSpend")
    Group.In -> listOf("Credit")
    Group.Due -> listOf("Bill", "Statement")
    Group.Code -> listOf("Otp")
}

object Items {
    private val stage = mapOf(Stage.Placed to "placed", Stage.Shipped to "shipped", Stage.Out to "out for delivery", Stage.Delivered to "delivered", Stage.Update to "update")

    fun of(e: Event, r: Raw, v: Verdict, learned: String?, since: Long = Long.MIN_VALUE): Item {
        val unsure = v is Verdict.Unsure
        val base = Item(
            kind = e.kind.name, at = r.at, src = r.source.name, title = "", state = if (unsure) State.ASK else State.SETTLED, conf = v.confidence,
            body = Body.keep(e, v.tags, r.body, r.at, since), tags = v.tags.joinToString(",").ifEmpty { null },
        )
        return when (e) {
            is Event.Otp -> base.copy(title = e.service ?: "Code", code = e.code, expires = e.expiresAt, note = e.purpose.orEmpty())
            is Event.Move -> {
                val credit = e is Event.Credit
                val named = Category.of(e.merchant, credit)
                val cat = learned ?: v.cat?.takeIf { named == Category.Other }?.let { Labels.category(it.label) }?.label ?: Category.of(e).label
                base.copy(
                    title = e.merchant ?: e.bank?.let { "$it ${if (credit) "credit" else "debit"}" } ?: if (credit) "Credit" else "Debit",
                    paise = e.paise, currency = e.currency, last4 = e.last4, bank = e.bank, merchant = e.merchant,
                    mode = e.mode.name, category = cat,
                    state = when {
                        unsure -> State.ASK
                        cat == Category.Other.label && e.merchant != null -> State.CHECK
                        else -> State.SETTLED
                    },
                )
            }
            is Event.Bill -> base.copy(
                title = e.biller?.let { "$it bill" } ?: "Bill", paise = e.paise, currency = e.currency, last4 = e.last4,
                merchant = e.biller, due = e.due?.toEpochDay(), minPaise = e.minPaise, category = Category.Bills.label,
            )
            is Event.Statement -> base.copy(
                title = "${e.bank ?: "Card"} card bill", paise = e.paise, last4 = e.last4, bank = e.bank, merchant = e.bank,
                due = e.due?.toEpochDay(), minPaise = e.minPaise, category = Category.Bills.label,
            )
            is Event.Delivery -> base.copy(title = "${e.merchant ?: "Order"} ${stage.getValue(e.stage)}", merchant = e.merchant)
            is Event.Travel -> {
                val c = Suggest.travel(e, r.title + "\n" + r.body, r.at, ZoneId.systemDefault())
                base.copy(title = "${e.what} booking", due = e.date?.toEpochDay(), note = c?.note.orEmpty(), start = c?.start, end = c?.end)
            }
            Event.Personal -> {
                val c = Suggest.chat(r.sender, r.title + "\n" + r.body, r.at, ZoneId.systemDefault())
                base.copy(
                    title = r.sender,
                    note = (r.title.takeIf { it != r.sender && it.isNotBlank() }?.plus(": ") ?: "") + r.body.take(160),
                    state = State.CHECK, start = c?.start, end = c?.end,
                )
            }
            Event.Unknown, Event.Alert, Event.Spam -> base.copy(title = r.title.ifBlank { r.sender }, note = r.body.take(160))
            Event.Promo -> base.copy(title = r.sender)
        }
    }
}
