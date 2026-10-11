package app.companion.core

import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer

object BridgeWire {
    const val JSON = BulkFrame.JSON
    const val AUDIO = BulkFrame.AUDIO
    const val VIDEO = BulkFrame.VIDEO
    const val INPUT = BulkFrame.INPUT
    const val MAX = 1 shl 20

    fun write(out: OutputStream, type: Int, body: ByteArray) {
        if (body.size > MAX) throw IOException("size")
        out.write(ByteBuffer.allocate(5 + body.size).putInt(body.size).put(type.toByte()).put(body).array())
        out.flush()
    }

    fun read(input: DataInputStream): Pair<Int, ByteArray> {
        val n = input.readInt()
        if (n < 0 || n > MAX) throw IOException("size")
        val t = input.readUnsignedByte()
        return t to ByteArray(n).also(input::readFully)
    }
}
