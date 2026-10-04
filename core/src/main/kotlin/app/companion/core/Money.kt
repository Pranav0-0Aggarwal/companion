package app.companion.core

import java.math.BigDecimal

internal data class Amt(val paise: Long, val currency: String, val at: Int, val end: Int)

internal object Money {
    private const val NUM = "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
    private val pre = Regex("(?i)(?<![a-z])(rs\\.?|inr|₹|usd|\\$|eur|€|gbp|£)\\s*$NUM")
    private val post = Regex("(?i)(?<![0-9,.])$NUM\\s*(inr|rs\\.?|usd)(?![a-z])")
    private val skip = Regex("(?i)bal|avl|avail|limit|outstanding")

    private fun currency(s: String) = when (s.lowercase()) {
        "rs", "rs.", "inr", "₹" -> "INR"
        "usd", "$" -> "USD"
        "eur", "€" -> "EUR"
        else -> "GBP"
    }

    private fun paise(s: String): Long? =
        runCatching { BigDecimal(s.replace(",", "")).movePointRight(2).toBigInteger().longValueExact() }.getOrNull()

    fun all(t: String): List<Amt> {
        val a = pre.findAll(t).mapNotNull { m ->
            paise(m.groupValues[2])?.let { Amt(it, currency(m.groupValues[1]), m.range.first, m.range.last + 1) }
        }
        val b = post.findAll(t).mapNotNull { m ->
            paise(m.groupValues[1])?.let { Amt(it, currency(m.groupValues[2]), m.range.first, m.range.last + 1) }
        }
        return (a + b).sortedBy { it.at }.toList()
    }

    fun txn(t: String): Amt? = all(t).firstOrNull { !skip.containsMatchIn(t.substring(maxOf(0, it.at - 24), it.at)) }

    fun after(t: String, label: Regex): Amt? {
        val m = label.find(t) ?: return null
        return all(t).firstOrNull { it.at >= m.range.last && it.at - m.range.last <= 28 }
    }
}
