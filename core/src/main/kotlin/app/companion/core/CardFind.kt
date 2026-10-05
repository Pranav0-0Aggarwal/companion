package app.companion.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class Seen(val kind: String, val bank: String?, val last4: String?, val at: Long, val due: Long?, val text: String)

class Found(val bank: String, val last4: String, val stmtDay: Int?, val dueDay: Int?, val limit: Long?, val avail: Long?) {
    val ready get() = stmtDay != null && dueDay != null
    val key get() = "${Cycles.issuer(bank)}:$last4"
}

class Limits(val credit: Long?, val avail: Long?)

object CardFind {
    private val debit = Regex("(?i)debit\\s*card|\\ba/c\\b|\\bsavings?\\b")
    private val avail = Regex("(?i)(?:available|avl\\.?|avail\\.?)\\s*(?:credit\\s*|cr\\.?\\s*)?limit")
    private val credit = Regex("(?i)(?:total\\s+)?(?:credit|cr\\.?)\\s*limit")
    private const val LAG = 20

    fun limits(text: String): Limits {
        val a = Money.after(text, avail)?.takeIf { it.currency == "INR" }?.paise
        val rest = avail.replace(text) { " ".repeat(it.value.length) }
        return Limits(Money.after(rest, credit)?.takeIf { it.currency == "INR" }?.paise, a)
    }

    private fun mid(l: List<Int>) = l.sorted().let { it[it.size / 2] }

    private fun wrap(d: Int) = (d - 1) % 28 + 1

    fun of(seen: List<Seen>, zone: ZoneId = ZoneId.systemDefault()): List<Found> =
        seen.filter { (it.kind == "Statement" || it.kind == "CardSpend") && !it.bank.isNullOrBlank() && it.last4?.length == 4 && !debit.containsMatchIn(it.text) }
            .groupBy { "${Cycles.issuer(it.bank)}:${it.last4}" }
            .values.mapNotNull { g ->
                val stmts = g.filter { it.kind == "Statement" }
                if (stmts.isEmpty() && g.size < 2) return@mapNotNull null
                val day = stmts.map { Instant.ofEpochMilli(it.at).atZone(zone).dayOfMonth }.takeIf { it.isNotEmpty() }?.let(::mid)
                val due = stmts.mapNotNull { it.due }.map { LocalDate.ofEpochDay(it).dayOfMonth }.takeIf { it.isNotEmpty() }?.let(::mid)
                val lim = g.sortedByDescending { it.at }.map { limits(it.text) }
                val bank = (stmts.ifEmpty { g }).groupingBy { it.bank!! }.eachCount().maxBy { it.value }.key
                Found(bank, g.first().last4!!, day ?: due?.let { wrap(it + 28 - LAG) }, due ?: day?.let { wrap(it + LAG) }, lim.firstNotNullOfOrNull { it.credit }, lim.firstNotNullOfOrNull { it.avail })
            }
            .sortedWith(compareByDescending<Found> { it.ready }.thenBy { it.bank }.thenBy { it.last4 })
}
