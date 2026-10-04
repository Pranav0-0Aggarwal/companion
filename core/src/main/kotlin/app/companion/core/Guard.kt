package app.companion.core

data class Vitals(val thermal: Int, val saver: Boolean, val charging: Boolean, val pct: Int)

data class Wait(val charging: Boolean, val idle: Boolean, val notLow: Boolean, val delay: Long) {
    val free get() = !charging && !idle && !notLow && delay == 0L
}

object Guard {
    const val LIGHT = 1
    const val MODERATE = 2
    const val FLOOR = 20
    const val NAP = 1000L
    const val WARM = "Phone is warm"
    const val SAVER = "Battery saver on"
    const val LOW = "Battery below 20%"
    const val CHARGER = "Waiting for charger and idle"
    const val RESUMING = "Resuming…"
    const val RECHECK = 15 * 60_000L

    fun hold(v: Vitals): String? = when {
        v.thermal >= MODERATE -> WARM
        v.saver -> SAVER
        !v.charging && v.pct < FLOOR -> LOW
        else -> null
    }

    fun waiting(battery: Boolean) = if (battery) RESUMING else CHARGER

    fun wait(why: String?, battery: Boolean) = Wait(!battery || why == SAVER, !battery, why == LOW, if (why == null) 0L else RECHECK)

    fun nap(v: Vitals) = if (v.thermal == LIGHT) NAP else 0L
}
