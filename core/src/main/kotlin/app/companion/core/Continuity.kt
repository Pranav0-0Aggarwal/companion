package app.companion.core

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SafeName {
    private const val MAX = 120
    private val bad = setOf(Character.CONTROL.toInt(), Character.FORMAT.toInt())

    fun of(name: String): String {
        val s = name.substringAfterLast('/').substringAfterLast('\\').replace(':', '_')
            .filter { Character.getType(it) !in bad }.trimStart { it == '.' || it.isWhitespace() }
        val dot = s.lastIndexOf('.').takeIf { it > 0 && s.length - it <= 17 } ?: s.length
        val ext = s.substring(dot)
        var stem = s.substring(0, dot)
        while (stem.toByteArray().size + ext.toByteArray().size > MAX) stem = stem.dropLast(if (stem.last().isLowSurrogate()) 2 else 1)
        return (stem + ext).ifEmpty { "file" }
    }
}

class Expiring<T>(private val ttl: Long, private val now: () -> Long, private val drop: (T) -> Unit = {}) {
    private val m = ConcurrentHashMap<String, Pair<Long, T>>()

    fun put(v: T): String {
        val t = now()
        m.entries.removeIf { (it.value.first < t).also { old -> if (old) drop(it.value.second) } }
        return UUID.randomUUID().toString().also { m[it] = t + ttl to v }
    }

    fun take(k: String?): T? {
        val (until, v) = m.remove(k ?: return null) ?: return null
        if (until >= now()) return v
        drop(v)
        return null
    }

    fun clear() {
        m.values.forEach { drop(it.second) }
        m.clear()
    }
}
