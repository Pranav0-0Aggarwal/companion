package app.companion.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToLong

data class TSpan(val start: Long, val end: Long)

object Trips {
    fun tag(at: Long, spans: List<Pair<Long, TSpan>>): Long? =
        spans.filter { at in it.second.start..it.second.end }
            .minWithOrNull(compareBy<Pair<Long, TSpan>>({ it.second.end - it.second.start }, { -it.second.start }))?.first
}

object Forex {
    data class Conv(val rate: Double, val from: String, val to: String)

    private val codes = "INR|USD|EUR|GBP|AED|SGD|THB|JPY|AUD|CAD|CHF|MYR|IDR|LKR|NPR|HKD|CNY|SAR|QAR|KRW|NZD|ZAR|VND|TRY|BDT|MVR|OMR|KWD|BHD"
    private const val NUM = "(\\d[\\d,]*(?:\\.\\d+)?)(?!\\d|\\.\\d)"
    private val cur = "(?<![A-Za-z])((?:rs\\.?|[₹\$€£]|$codes)(?![A-Za-z]))"
    private val amt = "(?:$cur\\s*$NUM|$NUM\\s*$cur)"
    private val eq = Regex("(?i)$amt\\s*=\\s*$amt")
    private val at = Regex("(?i)$amt\\s*@\\s*(?:$cur\\s*)?$NUM")
    private val key = Regex(
        "(?i)\\b(?:converted\\s+at|conversion\\s+rate|exchange\\s+rate|forex\\s+rate|conv\\.?\\s*rate|(?:at\\s+)?(?:the\\s+)?rate\\s+of|roe)\\b[\\s:@=]*(?:$cur\\s*)?$NUM(?!\\s*(?:[A-Za-z₹\$€£]{1,3}\\s*)?=)",
    )
    private val sym = mapOf("₹" to "INR", "\$" to "USD", "€" to "EUR", "£" to "GBP", "rs" to "INR", "rs." to "INR")

    private fun code(s: String) = s.lowercase().let { sym[it] ?: it.uppercase() }
    private fun num(s: String) = s.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 }
    private fun sane(c: Conv?) = c?.takeIf { it.rate.isFinite() && it.rate in 0.0001..100000.0 }

    fun parse(text: String): Conv? = sane(
        eq.find(text)?.let { m ->
            val g = m.groupValues
            val ca = code(g[1].ifEmpty { g[4] })
            val na = num(g[2].ifEmpty { g[3] })
            val cb = code(g[5].ifEmpty { g[8] })
            val nb = num(g[6].ifEmpty { g[7] })
            if (na == null || nb == null) null
            else when {
                cb == "INR" && ca != "INR" -> Conv(nb / na, ca, "INR")
                ca == "INR" && cb != "INR" -> Conv(na / nb, cb, "INR")
                else -> null
            }
        } ?: at.find(text)?.let { m ->
            val g = m.groupValues
            val from = code(g[1].ifEmpty { g[4] })
            val rate = num(g[6])
            if (rate == null || from == "INR" || g[5].isNotEmpty() && code(g[5]) != "INR") null else Conv(rate, from, "INR")
        } ?: key.find(text)?.let { m ->
            val g = m.groupValues
            val rate = num(g[2])
            if (rate == null || g[1].isNotEmpty() && code(g[1]) != "INR") null else Conv(rate, "", "INR")
        },
    )

    fun inr(paise: Long, c: Conv): Long = Math.round(if (c.to == "INR") paise * c.rate else paise / c.rate)
}

data class TItem(val id: Long, val at: Long, val paise: Long, val category: String?, val merchant: String?, val share: Double = 1.0)

data class Summary(val total: Long, val perDay: Long, val days: Int, val top: List<Pair<String, Long>>, val split: Map<String, Long>)

data class TravelHint(val what: String, val date: LocalDate?, val title: String)

data class Pick(val name: String, val start: Long, val end: Long)

object TripSummary {
    private const val WITHIN = 21L
    private const val TOP = 5
    private val dest = Regex("(?:(?i:\\b(?:to|in)\\b))\\s+([A-Z][a-z]+(?:\\s[A-Z][a-z]+)?)")
    private val stop = setOf("Your", "The", "Our", "Hotel", "Flight", "Train", "Booking", "Confirmed", "Confirmation", "Progress", "Advance", "Time", "Case", "Total", "Order", "Ticket", "Check")

    private fun cost(i: TItem) = (i.paise * i.share).roundToLong()

    fun of(items: List<TItem>, start: Long, end: Long, zone: ZoneId, who: Map<Long, List<String>> = emptyMap()): Summary {
        val total = items.sumOf { it.paise * it.share }.roundToLong()
        val s = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        val e = Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
        val days = (ChronoUnit.DAYS.between(s, e) + 1).toInt().coerceAtLeast(1)
        val top = items.groupBy { it.category ?: "other" }.map { (k, v) -> k to v.sumOf(::cost) }
            .sortedWith(compareBy<Pair<String, Long>>({ -it.second }, { it.first })).take(TOP)
        val split = HashMap<String, Long>()
        for (i in items) {
            val names = who[i.id].orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (names.isEmpty()) continue
            val a = cost(i)
            val n = names.size
            names.forEachIndexed { k, p -> split.merge(p, a / n + if (k < a % n) 1 else 0, Long::plus) }
        }
        return Summary(total, total / days, days, top, split)
    }

    private fun out(w: String) = w.contains("flight", true) || w.contains("train", true)
    private fun stay(w: String) = w.contains("stay", true)

    private fun place(titles: List<String>): String? = titles.firstNotNullOfOrNull { t ->
        dest.find(t)?.groupValues?.get(1)?.split(" ")?.takeWhile { it !in stop }?.joinToString(" ")?.takeIf { it.isNotEmpty() }
    }

    fun suggest(travel: List<TravelHint>, now: Long, zone: ZoneId): List<Pick> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val hs = travel.mapNotNull { h -> h.date?.takeIf { it >= today && (out(h.what) || stay(h.what)) }?.let { h to it } }.sortedBy { it.second }
        val picks = mutableListOf<Pick>()
        var i = 0
        while (i < hs.size) {
            val s = hs[i].second
            val g = hs.drop(i).takeWhile { it.second <= s.plusDays(WITHIN) }
            i += g.size
            val legs = g.filter { out(it.first.what) }.map { it.second }.distinct().size
            if (g.none { stay(it.first.what) } && legs < 2) continue
            val e = g.last().second
            val name = place(g.map { it.first.title })?.let { "Trip to $it" } ?: "Trip ${s.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${s.year}"
            picks += Pick(name, s.atStartOfDay(zone).toInstant().toEpochMilli(), e.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1)
        }
        return picks
    }
}
