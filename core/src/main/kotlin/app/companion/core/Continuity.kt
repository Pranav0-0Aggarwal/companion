package app.companion.core

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object ConvKey {
    fun of(pkg: String, shortcutId: String?, convTitle: String?, title: String) = "$pkg|${shortcutId ?: convTitle ?: title}"
}

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

object SendGuard {
    fun ok(scheme: String?, authority: String?, self: String, owner: (String) -> String?) =
        scheme == "content" && !authority.isNullOrEmpty() && owner(authority.substringAfterLast('@')) != self
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

    fun expire(k: String) {
        m.remove(k)?.let { drop(it.second) }
    }

    fun clear() {
        m.values.forEach { drop(it.second) }
        m.clear()
    }
}

class CallRestore(private val volume: Int, private val micMuted: Boolean) {
    private val spent = AtomicBoolean()

    fun take(): Pair<Int, Boolean>? = if (spent.compareAndSet(false, true)) volume to micMuted else null
}
