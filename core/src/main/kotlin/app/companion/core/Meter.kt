package app.companion.core

import java.util.Locale

class Rate(private val span: Long = 5000) {
    private val t = ArrayDeque<Long>()
    private val b = ArrayDeque<Long>()

    @Synchronized
    fun add(now: Long, bytes: Long) {
        t.addLast(now)
        b.addLast(bytes)
        while (t.size > 1 && now - t.first() > span) {
            t.removeFirst()
            b.removeFirst()
        }
    }

    @Synchronized
    fun bps(): Long = if (t.size < 2 || t.last() <= t.first()) 0 else (b.last() - b.first()) * 1000 / (t.last() - t.first())

    fun eta(left: Long): Long = bps().let { if (it <= 0) -1 else (left + it - 1) / it }
}

object Show {
    fun mb(b: Long) = String.format(Locale.ROOT, "%d", (b + 500_000) / 1_000_000)

    fun mbUp(b: Long) = ((b + 999_999) / 1_000_000).toString()

    fun speed(bps: Long) = if (bps < 1_000_000) "${bps / 1000} KB/s" else String.format(Locale.ROOT, "%.1f MB/s", bps / 1e6)

    fun eta(s: Long): String {
        if (s < 0) return "estimating time"
        if (s < 60) return "${maxOf(s, 1)} s left"
        val m = (s + 59) / 60
        return if (m < 60) "$m min left" else "${m / 60} h ${m % 60} min left"
    }

    fun pct(done: Long, total: Long) = if (total <= 0) 0 else (done * 100 / total).toInt().coerceIn(0, 100)
}
