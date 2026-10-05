package app.companion.core

import kotlin.math.abs

data class Slip(val at: Long, val due: Long?, val last4: String?, val name: String?)

object Cycles {
    private const val DAY = 86_400_000L
    private const val DUE = 10L
    private const val SPAN = 25 * DAY

    private class Row<T>(val i: Int, val t: T, val s: Slip)

    fun <T> of(items: List<T>, slip: (T) -> Slip): List<List<T>> {
        val rows = items.mapIndexed { i, t -> Row(i, t, slip(t)) }
        val (named, loose) = rows.partition { key(it.s) != null }
        val chains = named.groupBy { key(it.s) }.values.flatMap(::chain) + loose.map { listOf(it) }
        return chains.map { c -> c.sortedByDescending { it.s.at } }.sortedBy { it.first().i }.map { c -> c.map { it.t } }
    }

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
