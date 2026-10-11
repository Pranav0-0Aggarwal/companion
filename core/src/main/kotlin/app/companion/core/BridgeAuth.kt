package app.companion.core

import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object BridgeAuth {
    const val PORT = 38517
    private const val SERVER: Byte = 0x73
    private const val CLIENT: Byte = 0x63
    private const val NONCE = 16
    private const val MAC = 32

    fun proof(key: ByteArray, tag: Byte, nonce: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(byteArrayOf(tag) + nonce)

    fun ok(key: ByteArray, tag: Byte, nonce: ByteArray, mac: ByteArray) = MessageDigest.isEqual(proof(key, tag, nonce), mac)

    fun key(hex: String): ByteArray? =
        if (hex.length != 64 || hex.any { it !in '0'..'9' && it !in 'a'..'f' }) null
        else ByteArray(32) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    fun client(i: DataInputStream, o: DataOutputStream, key: ByteArray, rnd: (Int) -> ByteArray): Boolean {
        val cn = rnd(NONCE)
        o.write(cn)
        o.flush()
        val mac = ByteArray(MAC).also(i::readFully)
        val sn = ByteArray(NONCE).also(i::readFully)
        if (!ok(key, SERVER, cn, mac)) return false
        o.write(proof(key, CLIENT, sn))
        o.flush()
        return true
    }

    fun server(i: DataInputStream, o: DataOutputStream, key: ByteArray, rnd: (Int) -> ByteArray): Boolean {
        val cn = ByteArray(NONCE).also(i::readFully)
        val sn = rnd(NONCE)
        o.write(proof(key, SERVER, cn) + sn)
        o.flush()
        return ok(key, CLIENT, sn, ByteArray(MAC).also(i::readFully))
    }
}
