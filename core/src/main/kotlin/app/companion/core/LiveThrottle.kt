package app.companion.core

class LiveThrottle(private val gapMs: Long = 5_000) {
    private val seen = HashMap<String, Pair<String, Long>>()

    @Synchronized
    fun pass(key: String, state: String, now: Long): Boolean {
        val (s, at) = seen[key] ?: (null to 0L)
        if (s != null && s == state && now - at < gapMs) return false
        seen[key] = state to now
        return true
    }

    @Synchronized
    fun delay(key: String, now: Long) = seen[key]?.let { (gapMs - (now - it.second)).coerceAtLeast(0) } ?: 0L

    @Synchronized
    fun forget(key: String) {
        seen.remove(key)
    }
}

object LiveRule {
    private val cats = setOf("progress", "transport", "navigation", "stopwatch")

    fun ok(ongoing: Boolean, max: Int, chrono: Boolean, cat: String?) = ongoing && cat != "call" && (max > 0 || chrono || cat in cats)
}
