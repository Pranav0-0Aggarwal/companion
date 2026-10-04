package app.companion.core

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class Seg(val from: Long, val to: Long) {
    val len get() = to - from

    override fun equals(other: Any?) = other is Seg && other.from == from && other.to == to

    override fun hashCode() = from.hashCode() * 31 + to.hashCode()

    override fun toString() = "$from-$to"
}

object Ranges {
    fun merge(l: List<Seg>): List<Seg> {
        val out = ArrayList<Seg>()
        for (s in l.filter { it.len > 0 }.sortedBy { it.from }) {
            val last = out.lastOrNull()
            if (last != null && s.from <= last.to) out[out.size - 1] = Seg(last.from, maxOf(last.to, s.to)) else out.add(s)
        }
        return out
    }

    fun total(l: List<Seg>) = merge(l).sumOf { it.len }

    fun missing(size: Long, done: List<Seg>): List<Seg> {
        val out = ArrayList<Seg>()
        var at = 0L
        for (s in merge(done)) {
            if (s.from > at) out.add(Seg(at, minOf(s.from, size)))
            at = maxOf(at, s.to)
        }
        if (at < size) out.add(Seg(at, size))
        return out
    }

    fun chunks(gaps: List<Seg>, conns: Int, max: Long, min: Long): List<Seg> {
        val left = gaps.sumOf { it.len }
        val step = ((left + conns - 1) / conns).coerceIn(minOf(min, max), max)
        return gaps.flatMap { g -> (g.from until g.to step step).map { Seg(it, minOf(it + step, g.to)) } }
    }

    fun encode(size: Long, sha: String, l: List<Seg>) = (listOf("$size $sha") + merge(l).map { "${it.from} ${it.to}" }).joinToString("\n")

    fun decode(text: String, size: Long, sha: String): List<Seg> {
        val lines = text.lines().filter { it.isNotEmpty() }
        if (lines.firstOrNull() != "$size $sha") return emptyList()
        val out = lines.drop(1).map { ln ->
            val p = ln.split(' ')
            val a = p.getOrNull(0)?.toLongOrNull()
            val b = p.getOrNull(1)?.toLongOrNull()
            if (p.size != 2 || a == null || b == null || a < 0 || b <= a || b > size) return emptyList()
            Seg(a, b)
        }
        return merge(out)
    }
}

class Book(private val file: File, private val size: Long, private val sha: String) {
    private var spans = if (file.isFile) runCatching { Ranges.decode(file.readText(), size, sha) }.getOrDefault(emptyList()) else emptyList()

    @Synchronized
    fun add(s: Seg) {
        if (s.len <= 0) return
        spans = Ranges.merge(spans + s)
        val tmp = File(file.path + ".tmp")
        tmp.writeText(Ranges.encode(size, sha, spans))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    @Synchronized
    fun done() = Ranges.total(spans)

    @Synchronized
    fun missing() = Ranges.missing(size, spans)

    @Synchronized
    fun known() = spans.isNotEmpty()

    @Synchronized
    fun reset() {
        spans = emptyList()
        file.delete()
        File(file.path + ".tmp").delete()
    }
}
