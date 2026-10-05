package app.companion.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

object Rs {
    private fun group(n: Long): String {
        val s = n.toString()
        if (s.length <= 3) return s
        return s.dropLast(3).reversed().chunked(2).joinToString(",").reversed() + "," + s.takeLast(3)
    }

    fun of(paise: Long): String {
        val a = abs(paise)
        return "₹" + group(a / 100) + if (a % 100 == 0L) "" else "." + (a % 100).toString().padStart(2, '0')
    }

    fun signed(paise: Long) = (if (paise < 0) "-" else "+") + of(paise)
}

class Page<T>(val rows: List<T>, val n: Int, val total: Long)

data class Led(val id: Long, val at: Long, val who: String, val cat: String?, val last4: String?, val bank: String?, val paise: Long)

class Filt(val merchant: String?, val category: String?, val card: String?, val min: Long?, val max: Long?, val from: LocalDate, val to: LocalDate)

class Top(val who: String, val paise: Long, val n: Int)

class Cmp(val a: Long, val an: Int, val b: Long, val bn: Int) {
    val diff get() = a - b
    val pct get() = if (b > 0) Math.round(diff * 100.0 / b).toInt() else null
}

object Ledgers {
    const val CAP = 8
    private val four = Regex("\\d{4}")

    private fun card(l: Led, c: String): Boolean {
        val d = four.find(c)?.value
        return if (d != null) l.last4 == d else l.bank.orEmpty().contains(c.trim(), true)
    }

    private fun hit(l: Led, f: Filt, zone: ZoneId): Boolean {
        val d = Instant.ofEpochMilli(l.at).atZone(zone).toLocalDate()
        return !d.isBefore(f.from) && !d.isAfter(f.to) &&
            (f.category == null || f.category.equals(l.cat, true)) &&
            (f.merchant == null || l.who.contains(f.merchant, true)) &&
            (f.card == null || card(l, f.card)) &&
            (f.min == null || l.paise >= f.min) && (f.max == null || l.paise <= f.max)
    }

    fun run(rows: List<Led>, f: Filt, zone: ZoneId): Page<Led> {
        val m = rows.filter { hit(it, f, zone) }.sortedByDescending { it.at }
        return Page(m.take(CAP), m.size, m.sumOf { it.paise })
    }

    fun top(rows: List<Led>, n: Int, from: LocalDate, to: LocalDate, zone: ZoneId): Page<Top> {
        val f = Filt(null, null, null, null, null, from, to)
        val g = rows.filter { hit(it, f, zone) }.groupBy { it.who.trim().lowercase() }.values
            .map { l -> Top(l.first().who, l.sumOf { it.paise }, l.size) }.sortedByDescending { it.paise }
        return Page(g.take(n.coerceIn(1, CAP)), g.size, g.sumOf { it.paise })
    }

}

object Snip {
    private const val LEAD = 20
    private val white = Regex("\\s+")

    fun of(text: String, q: String, max: Int = 80): String {
        val s = text.replace(white, " ").trim()
        if (s.length <= max) return s
        val at = q.split(white).filter { it.isNotEmpty() }.map { s.indexOf(it, ignoreCase = true) }.filter { it >= 0 }.minOrNull() ?: 0
        val start = (at - LEAD).coerceAtLeast(0).coerceAtMost(s.length - max)
        val end = minOf(s.length, start + max)
        var a = start
        var b = end
        if (start > 0) a += if (Character.isHighSurrogate(s[a])) 2 else 1
        if (end < s.length) {
            b--
            if (Character.isHighSurrogate(s[b - 1])) b--
        }
        return (if (start > 0) "…" else "") + s.substring(a, b).trim() + (if (end < s.length) "…" else "")
    }
}

object Names {
    private val word = Regex("[a-z0-9]+")

    private fun key(s: String) = word.findAll(s.lowercase()).joinToString(" ") { it.value }

    fun find(q: String, all: List<String>): List<String> {
        val k = key(q)
        if (k.isEmpty()) return emptyList()
        val names = all.distinctBy(::key)
        return names.filter { key(it) == k }.ifEmpty { names.filter { k in key(it) } }
    }
}

class DueRow(val id: Long, val title: String, val name: String?, val last4: String?, val due: LocalDate?, val paise: Long, val min: Long?, val paid: Boolean)

object Dues {
    private val word = Regex("[a-z0-9]+")

    fun status(r: DueRow, today: LocalDate) = when {
        r.paid -> "paid"
        r.due != null && r.due.isBefore(today) -> "overdue"
        else -> "due"
    }

    fun pick(rows: List<DueRow>, q: String?): List<DueRow> {
        val t = q?.lowercase()?.let { word.findAll(it).map { m -> m.value }.toList() }.orEmpty()
        if (t.isEmpty()) return rows
        return rows.filter { r ->
            val hay = "${r.title} ${r.name.orEmpty()} ${r.last4.orEmpty()}".lowercase()
            t.all { it in hay }
        }
    }

    fun order(rows: List<DueRow>) = rows.sortedWith(compareBy<DueRow>({ it.paid }, { it.due == null }, { it.due }))
}

class Plastic(val name: String, val stmtDay: Int, val dueDay: Int)

class Window(val name: String, val stmt: LocalDate, val due: LocalDate, val days: Int)

object Best {
    fun rank(cards: List<Plastic>, today: LocalDate): List<Window> = cards.map { c ->
        val w = Cycle.window(c.stmtDay, c.dueDay, today)
        Window(c.name, w.from, w.to, ChronoUnit.DAYS.between(today, w.to).toInt())
    }.sortedByDescending { it.days }
}

class Wk(val from: LocalDate, val kg: Double)

class WeightView(val n: Int, val first: Double, val last: Double, val perWeek: Double?, val avg7: Double?, val weeks: List<Wk>)

object Weigh {
    fun view(points: List<Pair<LocalDate, Double>>, weeks: Int, today: LocalDate): WeightView? {
        val p = points.filter { it.first > today.minusWeeks(weeks.toLong()) && !it.first.isAfter(today) }.sortedBy { it.first }
        if (p.isEmpty()) return null
        val t = WeightNudge.trend(p)
        val w = p.groupBy { it.first.with(DayOfWeek.MONDAY) }.map { (d, l) -> Wk(d, Math.round(l.map { it.second }.average() * 10) / 10.0) }.sortedBy { it.from }
        return WeightView(p.size, p.first().second, p.last().second, t?.slopePerWeek, t?.avg7, w.takeLast(Ledgers.CAP))
    }
}

class MsgRow(val id: Long, val from: String, val at: Long, val snip: String)

class NeedRow(val id: Long, val kind: String, val title: String, val paise: Long, val at: Long, val state: String)

class MealSum(val slot: String, val kcal: Int, val items: List<String>)

class TripSum(val id: Long, val name: String, val from: LocalDate, val to: LocalDate, val active: Boolean, val total: Long, val days: Int)

object Shape {
    const val MSGS = 6

    private fun obj(vararg p: Pair<String, Any?>): Map<String, Any?> = p.filter { it.second != null }.toMap()

    private fun day(ms: Long, zone: ZoneId) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString()

    private fun hm(ms: Long, zone: ZoneId) = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().let { LocalTime.of(it.hour, it.minute) }.toString()

    private fun cut(s: String, n: Int) = if (s.length <= n) s else s.take(n - 1).trimEnd() + "…"

    private fun out(vararg p: Pair<String, Any?>) = Json.write(obj(*p))

    fun msgs(n: Int, rows: List<MsgRow>, zone: ZoneId) =
        out("n" to n, "rows" to rows.take(MSGS).map { obj("id" to Ref.item(it.id), "from" to cut(it.from, 20), "d" to day(it.at, zone), "t" to it.snip) })

    fun ledger(p: Page<Led>, zone: ZoneId) =
        out("n" to p.n, "amt" to Rs.of(p.total), "rows" to p.rows.map { obj("id" to Ref.item(it.id), "d" to day(it.at, zone), "who" to cut(it.who, 24), "cat" to it.cat, "amt" to Rs.of(it.paise)) })

    fun top(p: Page<Top>) = out("n" to p.n, "amt" to Rs.of(p.total), "rows" to p.rows.map { obj("who" to cut(it.who, 24), "amt" to Rs.of(it.paise), "n" to it.n) })

    fun compare(a: String, b: String, c: Cmp) =
        out("a" to obj("p" to a, "amt" to Rs.of(c.a), "n" to c.an), "b" to obj("p" to b, "amt" to Rs.of(c.b), "n" to c.bn), "diff" to Rs.signed(c.diff), "pct" to c.pct)

    fun needs(n: Int, rows: List<NeedRow>, zone: ZoneId) = out(
        "n" to n,
        "rows" to rows.map { obj("id" to Ref.item(it.id), "k" to it.kind, "t" to cut(it.title, 30), "amt" to it.paise.takeIf { p -> p > 0 }?.let(Rs::of), "d" to day(it.at, zone), "s" to it.state) },
    )

    fun dues(rows: List<DueRow>, today: LocalDate) = out(
        "rows" to rows.map { obj("id" to Ref.item(it.id), "t" to cut(it.title, 30), "due" to it.due?.toString(), "amt" to Rs.of(it.paise), "min" to it.min?.let(Rs::of), "st" to Dues.status(it, today)) },
    )

    fun best(w: List<Window>) = out("best" to w.firstOrNull()?.name, "rows" to w.map { obj("card" to cut(it.name, 24), "days" to it.days, "stmt" to it.stmt.toString(), "due" to it.due.toString()) })

    fun meals(day: LocalDate, kcal: Int, goal: Int?, rows: List<MealSum>) =
        out("d" to day.toString(), "kcal" to kcal, "goal" to goal, "rows" to rows.map { obj("slot" to it.slot, "kcal" to it.kcal, "items" to cut(it.items.joinToString(", "), 60)) })

    fun weight(v: WeightView) = out(
        "n" to v.n, "first" to v.first, "last" to v.last, "wk" to v.perWeek?.let { Math.round(it * 10) / 10.0 }, "avg7" to v.avg7?.let { Math.round(it * 10) / 10.0 },
        "rows" to v.weeks.map { obj("wk" to it.from.toString(), "kg" to it.kg) },
    )

    fun meets(day: LocalDate, rows: List<Meeting>, zone: ZoneId) = out(
        "d" to day.toString(), "n" to rows.size,
        "rows" to rows.take(Ledgers.CAP).map { obj("t" to cut(it.title.ifBlank { "Meeting" }, 30), "at" to hm(it.start, zone), "to" to hm(it.end, zone), "via" to Meets.source(it)?.let { s -> cut(s, 24) }) },
    )

    fun trips(rows: List<TripSum>) = out(
        "n" to rows.size,
        "rows" to rows.take(Ledgers.CAP).map { obj("id" to "t:${it.id}", "t" to cut(it.name, 24), "from" to it.from.toString(), "to" to it.to.toString(), "on" to it.active.takeIf { a -> a }, "amt" to Rs.of(it.total), "days" to it.days) },
    )
}
