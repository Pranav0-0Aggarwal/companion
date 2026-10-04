package app.companion.core

data class Vitals(val thermal: Int, val saver: Boolean, val charging: Boolean, val pct: Int)

object Guard {
    const val LIGHT = 1
    const val MODERATE = 2
    const val FLOOR = 20
    const val NAP = 1000L
    const val WARM = "Phone is warm"
    const val SAVER = "Battery saver on"
    const val LOW = "Battery below 20%"
    const val CHARGER = "Waiting for charger"

    fun hold(v: Vitals): String? = when {
        v.thermal >= MODERATE -> WARM
        v.saver -> SAVER
        !v.charging && v.pct < FLOOR -> LOW
        else -> null
    }

    fun nap(v: Vitals) = if (v.thermal == LIGHT) NAP else 0L
}
