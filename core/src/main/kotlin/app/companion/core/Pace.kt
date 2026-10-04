package app.companion.core

import kotlin.math.ceil

class Pace(private val alpha: Double = 0.3) {
    private var rate = 0.0

    fun add(n: Int, ms: Long) {
        if (n <= 0 || ms <= 0) return
        val r = n * 1000.0 / ms
        rate = if (rate == 0.0) r else alpha * r + (1 - alpha) * rate
    }

    fun eta(left: Int): Long? = if (rate <= 0.0) null else ceil(left / rate).toLong()
}
