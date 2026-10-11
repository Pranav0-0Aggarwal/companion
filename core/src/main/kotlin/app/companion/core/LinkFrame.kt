package app.companion.core

import java.io.ByteArrayOutputStream

object LinkFrames {
    const val FIRST = 0x80
    const val LAST = 0x40
    const val MAX = 256 * 1024

    fun split(msg: ByteArray, seq: Int, chunk: Int): List<ByteArray> {
        require(chunk > 0)
        val n = maxOf(1, (msg.size + chunk - 1) / chunk)
        return List(n) { i ->
            val from = i * chunk
            val head = (if (i == 0) FIRST else 0) or (if (i == n - 1) LAST else 0) or (seq and 63)
            byteArrayOf(head.toByte()) + msg.copyOfRange(from, minOf(msg.size, from + chunk))
        }
    }
}

class LinkReassembler {
    private var buf: ByteArrayOutputStream? = null

    fun feed(frame: ByteArray): ByteArray? {
        if (frame.isEmpty()) return null
        val head = frame[0].toInt() and 0xFF
        if (head and LinkFrames.FIRST != 0) buf = ByteArrayOutputStream()
        val b = buf ?: return null
        if (b.size() + frame.size - 1 > LinkFrames.MAX) {
            buf = null
            return null
        }
        b.write(frame, 1, frame.size - 1)
        return if (head and LinkFrames.LAST != 0) b.toByteArray().also { buf = null } else null
    }
}
