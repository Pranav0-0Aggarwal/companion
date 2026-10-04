package app.companion.core

object Cpu {
    private val need = setOf("asimddp", "i8mm")

    fun nux(info: String): Boolean {
        val rows = info.lineSequence().filter { it.startsWith("Features") }.map { it.substringAfter(':').trim().split(Regex("\\s+")).toSet() }.toList()
        return rows.isNotEmpty() && rows.all { it.containsAll(need) }
    }
}
