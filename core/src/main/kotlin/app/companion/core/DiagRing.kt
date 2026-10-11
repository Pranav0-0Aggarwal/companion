package app.companion.core

data class DiagLine(val at: Long, val bat: Int, val m: String)

class DiagRing(private val cap: Int = 400) {
    private val q = ArrayDeque<DiagLine>()

    @Synchronized
    fun add(l: DiagLine) {
        q.addLast(l)
        while (q.size > cap) q.removeFirst()
    }

    @Synchronized
    fun since(ts: Long, max: Int = 200): List<DiagLine> = q.filter { it.at > ts }.take(max)

    @Synchronized
    fun all(): List<DiagLine> = q.toList()
}
