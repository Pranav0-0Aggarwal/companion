package app.companion.data

import app.companion.core.Cycles
import app.companion.core.Flow
import app.companion.core.Flows
import app.companion.core.Merchant
import app.companion.core.Pay
import app.companion.core.Phase

internal val Item.cardBill get() = flow == Flow.CardBill.name || cardPay

internal fun Item.pay(): Pay? {
    if (!cardBill) return null
    if (credit) return Pay(at, last4, bank)
    val e = move() ?: return null
    return Pay(at, Flows.counterparty(e, body ?: "$title $note").singleOrNull(), (merchant ?: title).let(Merchant::brand)?.takeIf { it.bank }?.name)
}

internal suspend fun Dao.settle(now: Long): Int {
    val open = billsNow()
    if (open.isEmpty()) return 0
    val pays = cardPays(open.minOf { it.at }).mapNotNull(Item::pay)
    val done = Cycles.plan(open, Item::slip, pays, now).filter { it.phase == Phase.Paid || it.phase == Phase.Closed }
    done.forEach { close(it.items.map(Item::id), it.note) }
    return done.size
}
