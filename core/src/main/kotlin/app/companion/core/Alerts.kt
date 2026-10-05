package app.companion.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class Chan { Spend, Bill, Delivery }

data class Opts(
    val spend: Boolean = true,
    val bill: Boolean = true,
    val remind: Boolean = true,
    val delivery: Boolean = true,
    val digest: Boolean = true,
    val lock: Boolean = false,
    val hide: Boolean = false,
) {
    fun on(c: Chan) = when (c) {
        Chan.Spend -> spend
        Chan.Bill -> bill
        Chan.Delivery -> delivery
    }
}

data class Face(val title: String, val text: String? = null)

object Alerts {
    const val FRESH = 6 * 3_600_000L
    private const val SKEW = 5 * 60_000L
    private const val HOUR = 3_600_000L

    fun chan(k: Kind) = when (k) {
        Kind.Debit, Kind.CardSpend, Kind.Credit -> Chan.Spend
        Kind.Bill, Kind.Statement -> Chan.Bill
        Kind.Delivery -> Chan.Delivery
        else -> null
    }

    fun bit(c: Chan) = when (c) {
        Chan.Spend -> 16
        Chan.Bill -> 32
        Chan.Delivery -> 64
    }

    val seen = Chan.entries.fold(0) { a, c -> a or bit(c) }

    fun wants(o: Opts, k: Kind) = chan(k)?.let(o::on) == true

    fun fresh(at: Long, now: Long) = at in now - FRESH..now + SKEW

    fun shown(o: Opts, full: Face, hidden: Face) = if (o.lock) full else hidden

    fun spend(amount: String, title: String, category: String?, bank: String?, last4: String?, credit: Boolean): Face {
        val card = listOfNotNull(bank, last4?.let { "··$it" }).joinToString(" ").ifEmpty { null }
        val sub = listOfNotNull(category?.replaceFirstChar(Char::uppercase), card).joinToString(" · ").ifEmpty { null }
        return Face("${if (credit) "+" else ""}$amount · $title", sub)
    }

    fun total(amount: String, n: Int) = Face("Today · $amount across $n")

    fun bill(title: String, amount: String?, due: String?) =
        Face(listOfNotNull(title, amount).joinToString(" ") + (due?.let { " · due $it" }.orEmpty()))

    fun remindAt(due: LocalDate, now: Long, zone: ZoneId): Long =
        listOf(due.minusDays(1), due).map { Slot(it, LocalTime.of(9, 0)).millis(zone) }.firstOrNull { it > now } ?: (now + HOUR)

    fun dayStart(now: Long, zone: ZoneId): Long = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    fun untilDigest(now: Long, zone: ZoneId, at: LocalTime = LocalTime.of(20, 0)): Long {
        val t = Instant.ofEpochMilli(now).atZone(zone)
        val next = t.toLocalDate().atTime(at).atZone(zone).let { if (it.toInstant().toEpochMilli() > now) it else t.toLocalDate().plusDays(1).atTime(at).atZone(zone) }
        return next.toInstant().toEpochMilli() - now
    }
}

object Hidden {
    val spend = Face("New spending")
    val bill = Face("New bill")
    val due = Face("Bill due soon")
    val order = Face("Delivery update")
    val daily = Face("Daily summary")
    val meet = Face("Meeting soon")
    val brief = Face("Morning brief")
}

object Digest {
    private fun n(c: Int, one: String, many: String) = "$c ${if (c == 1) one else many}"

    fun text(need: Int, today: Int, tomorrow: Int, spent: String?): String? =
        listOfNotNull(
            need.takeIf { it > 0 }?.let { "${n(it, "message needs", "messages need")} you" },
            today.takeIf { it > 0 }?.let { "${n(it, "bill", "bills")} due today" },
            tomorrow.takeIf { it > 0 }?.let { "${n(it, "bill", "bills")} due tomorrow" },
            spent?.let { "$it spent today" },
        ).joinToString(" · ").ifEmpty { null }
}

data class BriefOpts(val on: Boolean = true, val at: Int = 8 * 60)

object Brief {
    fun face(name: String, parts: List<String>) =
        parts.takeIf { it.isNotEmpty() }?.let { Face(if (name.isBlank()) "Good morning" else "Morning, $name", it.joinToString(" · ")) }

    fun meeting(time: String) = "First meeting $time"

    fun bill(title: String, amount: String?, days: Int, weekday: String) =
        listOfNotNull(title, amount).joinToString(" ") + when (days) {
            0 -> " due today"
            1 -> " due tomorrow"
            else -> " due $weekday"
        }

    fun orders(merchant: String?, n: Int) = if (n == 1) "${merchant?.let { "$it order" } ?: "Order"} arriving" else "$n orders arriving"

    fun due(n: Int) = "$n ${if (n == 1) "bill" else "bills"} due today"
}

object Track {
    const val MAX = 300
    const val OUT = 3 * 3_600_000L
    const val DONE = 10 * 60_000L
    val points = listOf(100, 200)
    private val words = mapOf(Stage.Placed to "placed", Stage.Shipped to "shipped", Stage.Out to "out for delivery", Stage.Delivered to "delivered", Stage.Update to "update")

    fun word(s: Stage) = words.getValue(s)

    fun stage(title: String) = words.entries.firstOrNull { title == it.value || title.endsWith(" ${it.value}") }?.key

    fun at(s: Stage) = when (s) {
        Stage.Placed -> 0
        Stage.Shipped -> 100
        Stage.Out -> 200
        Stage.Delivered -> 300
        Stage.Update -> null
    }

    fun latest(stages: Collection<Stage>) = stages.filter { at(it) != null }.maxByOrNull { it.ordinal }

    fun key(merchant: String?, at: Long, zone: ZoneId) =
        merchant?.lowercase()?.filter(Char::isLetterOrDigit).orEmpty() + "@" + Instant.ofEpochMilli(at).atZone(zone).toLocalDate().toEpochDay()

    fun same(a: String?, b: String?): Boolean {
        val x = a?.lowercase()?.filter(Char::isLetterOrDigit).orEmpty()
        val y = b?.lowercase()?.filter(Char::isLetterOrDigit).orEmpty()
        return x.isNotEmpty() && y.isNotEmpty() && (x.contains(y) || y.contains(x))
    }
}

enum class Lane { Out, Code, Meet, Brief, Due, Spent }

object Cover {
    const val CODE = 10 * 60_000L
    const val WINDOW = 7
    const val MASK = "₹••••"

    fun code(at: Long, now: Long) = now - at < CODE

    const val FROM = 6 * 60
    const val TO = 10 * 60 + 30

    fun meet(mins: Long?) = mins != null && mins <= Meets.COVER / Meets.MIN

    fun brief(minute: Int, meeting: Boolean, due: Int) = minute in FROM..TO && (meeting || due > 0)

    fun lanes(out: Boolean, code: Boolean, due: Int?, spent: Int, meet: Boolean = false, brief: Boolean = false): List<Lane> = listOfNotNull(
        Lane.Out.takeIf { out },
        Lane.Code.takeIf { code },
        Lane.Meet.takeIf { meet },
        Lane.Brief.takeIf { brief && !out && !code && !meet },
        Lane.Due.takeIf { due != null && due <= WINDOW },
        Lane.Spent.takeIf { spent > 0 },
    )

    fun due(days: Int) = when {
        days < 0 -> "overdue ${-days}d"
        days == 0 -> "due today"
        days == 1 -> "due tomorrow"
        else -> "due in $days days"
    }

    fun amount(o: Opts, text: String) = if (o.hide) MASK else text
}
