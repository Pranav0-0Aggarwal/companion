package app.companion.core

import kotlin.math.abs

data class Leg(val id: Long, val out: Boolean, val paise: Long, val at: Long)

object Transfers {
    fun pairs(legs: List<Leg>): List<Pair<Leg, Leg>> {
        val used = HashSet<Long>()
        return legs.filter { it.out }.sortedBy { it.at }.mapNotNull { o ->
            legs.filter { !it.out && it.id !in used && it.paise == o.paise && abs(it.at - o.at) <= Fingerprint.WINDOW }
                .minByOrNull { abs(it.at - o.at) }
                ?.also { used += it.id }
                ?.let { o to it }
        }
    }
}
