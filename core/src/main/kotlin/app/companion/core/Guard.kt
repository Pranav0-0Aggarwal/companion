package app.companion.core

data class Vitals(val thermal: Int, val saver: Boolean, val charging: Boolean, val pct: Int)

data class Wait(val charging: Boolean, val notLow: Boolean, val delay: Long) {
    val free get() = !charging && !notLow && delay == 0L
}

object Guard {
    const val LIGHT = 1
    const val MODERATE = 2
    const val FLOOR = 20
    const val NAP = 3000L
    const val LIVE = 4
    const val WARM = "Phone is warm"
    const val SAVER = "Battery saver on"
    const val LOW = "Battery below 20%"
    const val CHARGER = "Waiting for charger"
    const val RESUMING = "Resuming…"
    const val RECHECK = 15 * 60_000L
    const val COOL = 3 * 60_000L

    fun hold(v: Vitals, charge: Boolean = false): String? = when {
        v.thermal >= MODERATE -> WARM
        v.saver -> SAVER
        charge && !v.charging -> CHARGER
        !v.charging && v.pct < FLOOR -> LOW
        else -> null
    }

    fun waiting(battery: Boolean) = if (battery) RESUMING else CHARGER

    fun wait(why: String?, battery: Boolean) = Wait(!battery || why == SAVER, why == LOW, when (why) {
        null, CHARGER -> 0L
        WARM -> COOL
        else -> RECHECK
    })

    fun threads(v: Vitals) = if (v.charging) 4 else 2

    fun prefill(v: Vitals) = if (v.charging || v.thermal < MODERATE) 4 else 2

    fun take(v: Vitals, n: Int, charge: Boolean) = if (!charge || hold(v, true) == null) n else n.coerceAtMost(LIVE)

    fun nap(v: Vitals) = if (v.thermal == LIGHT) NAP else 0L
}
