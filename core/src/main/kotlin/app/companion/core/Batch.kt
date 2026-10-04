package app.companion.core

data class Progress(val pos: Long, val cap: Long, val done: Int, val total: Int, val moved: Int, val ask: Int, val skip: Int, val sha: String?)

object Batch {
    const val SIZE = 32

    fun size(left: Int, size: Int = SIZE) = left.coerceIn(0, size)

    fun left(p: Progress) = (p.total - p.done).coerceAtLeast(0)

    fun start(pos: Long, cap: Long, sha: String?) = Progress(pos, cap, 0, 0, 0, 0, 0, sha)

    fun resume(saved: Progress?, sha: String?) = saved?.takeIf { it.sha == sha }

    fun fit(p: Progress, remaining: Int) = p.copy(total = p.done + remaining.coerceAtLeast(0))

    fun step(p: Progress, pos: Long, n: Int, moved: Int = 0, ask: Int = 0, skip: Int = 0) =
        p.copy(pos = pos, done = p.done + n, moved = p.moved + moved, ask = p.ask + ask, skip = p.skip + skip)
}
