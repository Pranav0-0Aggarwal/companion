package app.companion.core

import java.nio.ByteBuffer

object VideoFrame {
    const val CONFIG = 1
    const val KEY = 2
    const val MORE = 4
    const val HEAD = 9

    fun pack(flags: Int, pts: Long, data: ByteBuffer): ByteArray =
        ByteBuffer.allocate(HEAD + data.remaining()).put(flags.toByte()).putLong(pts).put(data).array()

    fun split(body: ByteArray, max: Int = BulkFrame.MAX - 1): List<ByteArray> {
        if (body.size <= max) return listOf(body)
        val step = max - HEAD
        return (HEAD until body.size step step).map { at ->
            val last = at + step >= body.size
            val flags = body[0].toInt() or (if (last) 0 else MORE)
            byteArrayOf(flags.toByte()) + body.copyOfRange(1, HEAD) + body.copyOfRange(at, minOf(at + step, body.size))
        }
    }

    fun fit(w: Int, h: Int, max: Int): Pair<Int, Int> {
        val m = maxOf(w, h, max)
        return maxOf(16, w * max / m / 16 * 16) to maxOf(16, h * max / m / 16 * 16)
    }
}
