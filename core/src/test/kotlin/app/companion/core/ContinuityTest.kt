package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContinuityTest {
    private val v = Json.obj(checkNotNull(javaClass.getResourceAsStream("/link-vectors.json")).use { it.readBytes().decodeToString() })
    private fun hex(h: String) = ByteArray(h.length / 2) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private fun str(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    @Suppress("UNCHECKED_CAST") private val b = v.getValue("bulk") as Map<String, String>

    @Test
    fun `bulk keys match vectors`() {
        assertEquals(b["k"], str(BulkCrypto.keys(hex(v.getValue("okm") as String), hex(b.getValue("token")), b.getValue("purpose"))))
    }

    @Test
    fun `phone reads mac frame and writes phone frame`() {
        val s = BulkCrypto.phone(hex(v.getValue("okm") as String), hex(b.getValue("token")), b.getValue("purpose"))
        val f = BulkFrame(s)
        val (t, body) = f.read(java.io.DataInputStream(hex(b.getValue("frame1")).inputStream()))
        assertEquals(BulkFrame.DATA, t)
        assertEquals("hello", body.decodeToString())
        assertEquals(b["frame2"], str(f.seal(BulkFrame.JSON, "{\"t\":\"done\"}".toByteArray(), counter = 3)))
    }

    @Test
    fun `replay and oversize are rejected`() {
        val s = BulkCrypto.phone(hex(v.getValue("okm") as String), hex(b.getValue("token")), b.getValue("purpose"))
        val f = BulkFrame(s)
        val wire = hex(b.getValue("frame1"))
        f.read(java.io.DataInputStream(wire.inputStream()))
        assertFailsWith<LinkError> { f.read(java.io.DataInputStream(wire.inputStream())) }
        assertFailsWith<LinkError> { f.read(java.io.DataInputStream(byteArrayOf(0, 2, 0, 0).inputStream())) }
        assertFailsWith<LinkError> { f.seal(BulkFrame.DATA, ByteArray(BulkFrame.MAX)) }
    }

    @Test
    fun `next counter increments when none given`() {
        val f = BulkFrame(BulkCrypto.phone(hex(v.getValue("okm") as String), hex(b.getValue("token")), b.getValue("purpose")))
        assertEquals(f.seal(BulkFrame.JSON, "x".toByteArray(), counter = 0).size, f.seal(BulkFrame.JSON, "x".toByteArray()).size)
    }

    @Test
    fun `pipe talks to a mac stand-in over loopback`() {
        val okm = ByteArray(64) { 7 }
        val token = ByteArray(16) { it.toByte() }
        java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { srv ->
            var err: Throwable? = null
            val t = Thread {
                try {
                    srv.accept().use { s ->
                        val inp = java.io.DataInputStream(s.getInputStream())
                        val tok = ByteArray(16).also(inp::readFully)
                        kotlin.test.assertContentEquals(token, tok)
                        val k = BulkCrypto.keys(okm, token, "files_in")
                        val mac = BulkFrame(LinkSession(k.copyOfRange(0, 32), k.copyOfRange(32, 64), 1, 2, aad = BulkCrypto.AAD))
                        s.getOutputStream().write(mac.seal(BulkFrame.DATA, "hi".toByteArray()))
                        val (ty, body) = mac.read(inp)
                        assertEquals(BulkFrame.JSON, ty)
                        assertEquals("{\"t\":\"done\"}", body.decodeToString())
                    }
                } catch (e: Throwable) {
                    err = e
                }
            }.apply { start() }
            val sock = java.net.Socket("127.0.0.1", srv.localPort).apply { getOutputStream().write(token) }
            BulkPipe(sock, BulkFrame(BulkCrypto.phone(okm, token, "files_in")), "files_in", null).use { p ->
                assertEquals("hi", p.read().second.decodeToString())
                p.sendJson(mapOf("t" to "done"))
            }
            t.join(2000)
            assertFalse(t.isAlive)
            err?.let { throw it }
        }
    }

    @Test
    fun `public ip is refused`() {
        assertFailsWith<IllegalArgumentException> { BulkAddr.check("8.8.8.8") }
        BulkAddr.check("192.168.1.20")
    }

    @Test
    fun `only rfc1918 literals pass`() {
        listOf("10.0.0.1", "172.16.0.1", "172.31.255.254", "192.168.0.1").forEach(BulkAddr::check)
        listOf("172.15.0.1", "172.32.0.1", "192.169.0.1", "11.0.0.1", "127.0.0.1", "010.0.0.1", "10.0.0", "10.0.0.256", "10.0.0.1.1", "localhost", "", "::1", "+10.0.0.1", "\u0661\u0660.0.0.1", "10.0.0.\u0661").forEach {
            assertFailsWith<IllegalArgumentException>(it) { BulkAddr.check(it) }
        }
    }

    @Test
    fun `live throttle passes new keys and state changes, holds repeats for 5s`() {
        val t = LiveThrottle()
        assertTrue(t.pass("k", "a", 0))
        assertFalse(t.pass("k", "a", 1_000))
        assertTrue(t.pass("k", "b", 1_500))
        assertFalse(t.pass("k", "b", 6_000))
        assertTrue(t.pass("k", "b", 6_500))
        t.forget("k")
        assertTrue(t.pass("k", "b", 6_600))
    }

    @Test
    fun `live rule needs ongoing plus progress, chronometer or a live category, never calls`() {
        assertTrue(LiveRule.ok(true, 100, false, null))
        assertTrue(LiveRule.ok(true, 0, true, null))
        listOf("progress", "transport", "navigation", "stopwatch").forEach { assertTrue(LiveRule.ok(true, 0, false, it)) }
        assertFalse(LiveRule.ok(false, 100, true, "progress"))
        assertFalse(LiveRule.ok(true, 0, false, "email"))
        assertFalse(LiveRule.ok(true, 100, true, "call"))
    }
}
