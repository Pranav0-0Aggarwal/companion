package app.companion.core

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BridgeAuthTest {
    private val key = ByteArray(32) { it.toByte() }
    private val other = ByteArray(32) { (it + 1).toByte() }
    private val nonce = ByteArray(16) { (it * 3).toByte() }

    private fun run(ck: ByteArray, sk: ByteArray): Pair<Boolean, Boolean> {
        val a = PipedOutputStream()
        val b = PipedInputStream(a, 4096)
        val c = PipedOutputStream()
        val d = PipedInputStream(c, 4096)
        var s = false
        val t = thread {
            s = runCatching { BridgeAuth.server(DataInputStream(b), DataOutputStream(c), sk) { n -> ByteArray(n) { 2 } } }.getOrDefault(false)
            c.close()
        }
        val cl = runCatching { BridgeAuth.client(DataInputStream(d), DataOutputStream(a), ck) { n -> ByteArray(n) { 1 } } }.getOrDefault(false)
        a.close()
        t.join()
        return cl to s
    }

    @Test
    fun `key parses exactly 64 lowercase hex chars`() {
        val hex = BridgeAuth.hex(key)
        assertEquals(64, hex.length)
        assertContentEquals(key, BridgeAuth.key(hex))
        assertNull(BridgeAuth.key(hex.dropLast(2)))
        assertNull(BridgeAuth.key(hex + "00"))
        assertNull(BridgeAuth.key(hex.uppercase().replace("0", "A")))
        assertNull(BridgeAuth.key("g".repeat(64)))
        assertNull(BridgeAuth.key(""))
    }

    @Test
    fun `proof is deterministic and matches hmac over tag and nonce`() {
        val want = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(byteArrayOf(0x73) + nonce)
        assertContentEquals(want, BridgeAuth.proof(key, 0x73, nonce))
        assertContentEquals(BridgeAuth.proof(key, 0x73, nonce), BridgeAuth.proof(key, 0x73, nonce))
        assertEquals(32, want.size)
        assertNotNull(BridgeAuth.key(BridgeAuth.hex(want)))
    }

    @Test
    fun `tags are not interchangeable`() {
        val s = BridgeAuth.proof(key, 0x73, nonce)
        assertTrue(BridgeAuth.ok(key, 0x73, nonce, s))
        assertFalse(BridgeAuth.ok(key, 0x63, nonce, s))
        assertFalse(BridgeAuth.ok(other, 0x73, nonce, s))
    }

    @Test
    fun `same key succeeds on both sides`() = assertEquals(true to true, run(key, key))

    @Test
    fun `server rejects a client with the wrong key`() {
        val (_, s) = run(other, key)
        assertFalse(s)
    }

    @Test
    fun `client rejects a server with the wrong key`() {
        val (c, s) = run(key, other)
        assertFalse(c)
        assertFalse(s)
    }
}
