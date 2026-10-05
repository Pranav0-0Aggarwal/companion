package app.companion.data

import app.companion.core.Cycles
import app.companion.core.Flow
import app.companion.core.Pay
import app.companion.core.Pays
import app.companion.core.Phase

internal val Item.cardBill get() = flow == Flow.CardBill.name || cardPay

internal fun Item.pay(): Pay? = move()?.takeIf { cardBill }?.let { Pays.of(it, body ?: "$title $note", at) }

internal suspend fun Dao.settle(now: Long): Int {
    val open = billsNow()
    if (open.isEmpty()) return 0
    val pays = cardPays(open.minOf { it.at }).mapNotNull(Item::pay)
    val done = Cycles.plan(open, Item::slip, pays, now).filter { it.phase == Phase.Paid || it.phase == Phase.Closed }
    done.forEach { close(it.items.map(Item::id), it.note) }
    return done.size
}
