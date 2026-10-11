package app.companion.core

import java.io.DataInputStream
import java.nio.ByteBuffer

object BulkCrypto {
    val AAD = "CB1".toByteArray()

    fun keys(okm: ByteArray, token: ByteArray, purpose: String) = LinkCrypto.hkdf(okm, token, "companion-bulk v1".toByteArray() + purpose.toByteArray(), 64)

    fun phone(okm: ByteArray, token: ByteArray, purpose: String) = keys(okm, token, purpose).let {
        LinkSession(it.copyOfRange(32, 64), it.copyOfRange(0, 32), LinkSession.PHONE_TO_MAC, LinkSession.MAC_TO_PHONE, aad = AAD)
    }
}

class BulkFrame(private val s: LinkSession) {
    fun seal(type: Int, body: ByteArray, counter: Long? = null): ByteArray {
        if (body.size >= MAX) throw LinkError("size")
        val plain = byteArrayOf(type.toByte()) + body
        val sealed = if (counter == null) s.seal(plain) else s.seal(plain, counter)
        return ByteBuffer.allocate(4).putInt(sealed.size).array() + sealed
    }

    fun read(input: DataInputStream): Pair<Int, ByteArray> {
        val n = input.readInt()
        if (n < 0 || n > MAX + 25) throw LinkError("size")
        val plain = s.open(ByteArray(n).also(input::readFully))
        val t = plain.firstOrNull()?.toInt() ?: -1
        if (t !in JSON..INPUT) throw LinkError("type")
        return t to plain.copyOfRange(1, plain.size)
    }

    companion object {
        const val JSON = 0
        const val DATA = 1
        const val AUDIO = 2
        const val VIDEO = 3
        const val INPUT = 4
        const val MAX = 65_537
    }
}
