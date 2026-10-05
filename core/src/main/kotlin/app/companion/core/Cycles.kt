package app.companion.core

import kotlin.math.abs

data class Slip(val at: Long, val due: Long?, val last4: String?, val name: String?, val kept: Boolean = false)

data class Pay(val at: Long, val last4: String?, val bank: String?)

object Pays {
    fun of(e: Event.Move, text: String, at: Long) =
        if (e is Event.Credit) Pay(at, e.last4, e.bank) else Pay(at, Flows.counterparty(e, text).singleOrNull(), Merchant.brand(e.merchant, true)?.takeIf { it.bank }?.name)
}

enum class Phase { Open, Old, Paid, Closed }

class BillCycle<T>(val items: List<T>, val phase: Phase, val note: String = "") {
    val head get() = items.first()
}

object Paid {
    class Note(val by: String, val name: String?, val at: Long)

    const val PAY = "pay"
    const val NEWER = "new"
    const val ME = "me"
    const val KEEP = "keep"

    fun note(by: String, at: Long, name: String? = null) = "$by|${name.orEmpty().replace('|', ' ')}|$at"

    fun read(note: String): Note? {
        val p = note.split('|')
        val at = p.getOrNull(2)?.toLongOrNull()
        return if (p.size == 3 && at != null && p[0] in setOf(PAY, NEWER, ME)) Note(p[0], p[1].ifEmpty { null }, at) else null
    }
}

object Cycles {
    private const val DAY = 86_400_000L
    private const val DUE = 10L
    private const val SPAN = 25 * DAY
    const val OLD_DUE = 45L
    const val OLD_MSG = 60L

    private class Row<T>(val i: Int, val t: T, val s: Slip)

    fun <T> of(items: List<T>, slip: (T) -> Slip): List<List<T>> {
        val rows = items.mapIndexed { i, t -> Row(i, t, slip(t)) }
        val (named, loose) = rows.partition { key(it.s) != null }
        val chains = named.groupBy { key(it.s) }.values.flatMap(::chain) + loose.map { listOf(it) }
        return chains.map { c -> c.sortedByDescending { it.s.at } }.sortedBy { it.first().i }.map { c -> c.map { it.t } }
    }

    fun <T> plan(items: List<T>, slip: (T) -> Slip, pays: List<Pay> = emptyList(), now: Long): List<BillCycle<T>> {
        val day = Math.floorDiv(now, DAY)
        val groups = of(items, slip)
        val newest = groups.mapNotNull { g -> key(slip(g.first()))?.let { it to g } }.groupBy({ it.first }, { it.second }).mapValues { (_, l) -> l.maxBy { slip(it.first()).at } }
        return groups.map { g ->
            val s = slip(g.first())
            val k = key(s)
            val by = pays.filter { it.at > s.at && matches(it, s) }.minByOrNull { it.at }
            val head = k?.let { newest[it] }?.takeIf { it !== g }?.first()?.let(slip)
            when {
                by != null -> BillCycle(g, Phase.Paid, Paid.note(Paid.PAY, by.at, by.bank))
                head != null -> BillCycle(g, Phase.Closed, Paid.note(Paid.NEWER, head.at))
                !s.kept && old(s, day, now) -> BillCycle(g, Phase.Old)
                else -> BillCycle(g, Phase.Open)
            }
        }
    }

    private fun old(s: Slip, day: Long, now: Long) = if (s.due != null) day - s.due > OLD_DUE else now - s.at > OLD_MSG * DAY

    private fun matches(p: Pay, s: Slip): Boolean {
        val a = p.last4?.takeIf { it.isNotBlank() }
        val b = s.last4?.takeIf { it.isNotBlank() }
        if (a != null && b != null) return a == b
        val x = issuer(p.bank)
        return x != null && x == issuer(s.name)
    }

    private val noise = setOf("bank", "card", "cards", "credit", "ltd", "limited")

    fun issuer(s: String?) = s?.let { BrandDb.words(it).filter { w -> w !in noise }.joinToString("") }?.takeIf { it.isNotEmpty() }

    private fun key(s: Slip) =
        s.last4?.takeIf { it.isNotBlank() }?.let { "#$it" } ?: s.name?.filterNot(Char::isWhitespace)?.lowercase()?.takeIf { it.isNotEmpty() }

    private fun <T> chain(rows: List<Row<T>>): List<List<Row<T>>> {
        val out = mutableListOf<MutableList<Row<T>>>()
        rows.sortedBy { it.s.at }.forEach { r ->
            val cur = out.lastOrNull()
            if (cur != null && same(cur.last().s, r.s)) cur += r else out += mutableListOf(r)
        }
        return out
    }

    private fun same(a: Slip, b: Slip): Boolean {
        val x = a.due
        val y = b.due
        return if (x != null && y != null) abs(x - y) <= DUE else abs(a.at - b.at) <= SPAN
    }
}
