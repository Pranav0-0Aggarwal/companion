package app.companion.core

import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.NamedParameterSpec
import java.security.spec.XECPrivateKeySpec
import java.security.spec.XECPublicKeySpec
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class LinkError(why: String) : Exception(why)

class LinkSession(private val out: ByteArray, private val inn: ByteArray, private val outDir: Int, private val inDir: Int, val okm: ByteArray = ByteArray(0)) {
    private var sent = 0L
    private var seen = -1L

    fun seal(plain: ByteArray, counter: Long = sent++): ByteArray {
        val c = Cipher.getInstance(GCM).apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(out, AES), GCMParameterSpec(TAG, nonce(outDir, counter)))
            updateAAD(AAD)
        }
        return byteArrayOf(SEALED) + ByteBuffer.allocate(8).putLong(counter).array() + c.doFinal(plain)
    }

    fun open(msg: ByteArray): ByteArray {
        if (msg.size < 1 + 8 + TAG / 8 || msg[0] != SEALED) throw LinkError("shape")
        val counter = ByteBuffer.wrap(msg, 1, 8).long
        if (counter < 0 || counter <= seen) throw LinkError("replay")
        val c = Cipher.getInstance(GCM).apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(inn, AES), GCMParameterSpec(TAG, nonce(inDir, counter)))
            updateAAD(AAD)
        }
        val plain = try {
            c.doFinal(msg, 9, msg.size - 9)
        } catch (_: AEADBadTagException) {
            throw LinkError("auth")
        }
        seen = counter
        return plain
    }

    private fun nonce(dir: Int, counter: Long) = ByteBuffer.allocate(12).putInt(dir).putLong(counter).array()

    companion object {
        const val SEALED: Byte = 1
        const val MAC_TO_PHONE = 1
        const val PHONE_TO_MAC = 2
        private const val GCM = "AES/GCM/NoPadding"
        private const val AES = "AES"
        private const val TAG = 128
        private val AAD = "CL1".toByteArray()
    }
}

class LinkKeys(val priv: ByteArray, val pub: ByteArray)

object LinkCrypto {
    private val curve = NamedParameterSpec.X25519
    private val base = ByteArray(32).also { it[0] = 9 }
    private val info = "companion-link v1".toByteArray()
    private val rng = SecureRandom()

    fun random(n: Int) = ByteArray(n).also(rng::nextBytes)

    fun keys() = random(32).let { LinkKeys(it, pub(it)) }

    fun pub(priv: ByteArray) = shared(priv, base)

    fun shared(priv: ByteArray, peer: ByteArray): ByteArray {
        val f = KeyFactory.getInstance("XDH")
        val u = peer.copyOf().also { it[31] = (it[31].toInt() and 0x7f).toByte() }
        return KeyAgreement.getInstance("XDH").run {
            init(f.generatePrivate(XECPrivateKeySpec(curve, priv)))
            doPhase(f.generatePublic(XECPublicKeySpec(curve, BigInteger(1, u.reversedArray()))), true)
            generateSecret()
        }
    }

    fun okm(shared: ByteArray, salt: ByteArray, nm: ByteArray, np: ByteArray) = hkdf(shared, salt, info + nm + np, 64)

    fun session(priv: ByteArray, macPub: ByteArray, salt: ByteArray, nm: ByteArray, np: ByteArray): LinkSession {
        val k = okm(shared(priv, macPub), salt, nm, np)
        return LinkSession(k.copyOfRange(32, 64), k.copyOfRange(0, 32), LinkSession.PHONE_TO_MAC, LinkSession.MAC_TO_PHONE, k)
    }

    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, len: Int): ByteArray {
        val prk = hmac(salt, ikm)
        var t = ByteArray(0)
        var out = ByteArray(0)
        var i = 1
        while (out.size < len) {
            t = hmac(prk, t, info, byteArrayOf(i++.toByte()))
            out += t
        }
        return out.copyOf(len)
    }

    private fun hmac(key: ByteArray, vararg parts: ByteArray) = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        parts.forEach(::update)
        doFinal()
    }

    fun b64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)

    fun b64u(b: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(b)

    fun unb64(s: String): ByteArray = Base64.getUrlDecoder().decode(s.replace('+', '-').replace('/', '_'))
}
