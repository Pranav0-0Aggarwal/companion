package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun `safe names never escape and never empty`() {
        assertEquals("authorized_keys", SafeName.of("../../.ssh/authorized_keys"))
        assertEquals("file", SafeName.of(".."))
        assertEquals("file", SafeName.of(""))
        assertEquals("b_c.txt", SafeName.of("a/b:c.txt"))
        assertEquals("evil.txt", SafeName.of("..\\..\\evil.txt"))
        assertEquals("hidden.txt", SafeName.of(" \u0000\n.hid\u202Eden.txt"))
        assertTrue(SafeName.of("x".repeat(300) + ".jpg").let { it.toByteArray().size <= 120 && it.endsWith(".jpg") })
        assertTrue(SafeName.of("\u00e9".repeat(200) + ".pdf").let { it.toByteArray().size <= 120 && it.endsWith(".pdf") })
        assertTrue(SafeName.of("x".repeat(300)).toByteArray().size <= 120)
        assertTrue(SafeName.of("e" + "\u0301".repeat(200) + ".pdf").let { !it.startsWith(".") && it.toByteArray().size <= 120 && it.endsWith(".pdf") })
    }

    @Test
    fun `pending entries expire and are dropped once`() {
        var now = 0L
        val dropped = mutableListOf<String>()
        val p = Expiring<String>(60_000, { now }) { dropped += it }
        val a = p.put("a")
        assertEquals("a", p.take(a))
        assertNull(p.take(a))
        val b = p.put("b")
        now = 60_001
        assertNull(p.take(b))
        assertEquals(listOf("b"), dropped)
        val c = p.put("c")
        p.clear()
        assertNull(p.take(c))
        assertNull(p.take(null))
        assertEquals(listOf("b", "c"), dropped)
    }

    @Test
    fun `send guard accepts only foreign content providers`() {
        val owner = { a: String -> mapOf("com.other.docs" to "com.other", "app.companion.files" to "app.companion")[a] }
        assertTrue(SendGuard.ok("content", "com.other.docs", "app.companion", owner))
        assertTrue(SendGuard.ok("content", "10@com.other.docs", "app.companion", owner))
        assertTrue(SendGuard.ok("content", "unknown.provider", "app.companion", owner))
        assertFalse(SendGuard.ok("content", "app.companion.files", "app.companion", owner))
        assertFalse(SendGuard.ok("content", "0@app.companion.files", "app.companion", owner))
        assertFalse(SendGuard.ok("content", "x@y@app.companion.files", "app.companion", owner))
        assertFalse(SendGuard.ok("file", "", "app.companion", owner))
        assertFalse(SendGuard.ok("file", null, "app.companion", owner))
        assertFalse(SendGuard.ok(null, "com.other.docs", "app.companion", owner))
        assertFalse(SendGuard.ok("content", null, "app.companion", owner))
        assertFalse(SendGuard.ok("android.resource", "com.other.docs", "app.companion", owner))
    }

    @Test
    fun `expire drops a pending entry once`() {
        val dropped = mutableListOf<String>()
        val p = Expiring<String>(60_000, { 0L }) { dropped += it }
        val a = p.put("a")
        p.expire(a)
        p.expire(a)
        assertNull(p.take(a))
        assertEquals(listOf("a"), dropped)
        val b = p.put("b")
        assertEquals("b", p.take(b))
        p.expire(b)
        assertEquals(listOf("a"), dropped)
    }

    private class Mem : FileFrames.Sink {
        val files = mutableListOf<Pair<String, java.io.ByteArrayOutputStream>>()
        val log = mutableListOf<String>()
        override fun begin(name: String, size: Long) { files += name to java.io.ByteArrayOutputStream(); log += "begin $name" }
        override fun write(b: ByteArray) = files.last().second.write(b)
        override fun finish() { log += "finish" }
        override fun abort() { log += "abort" }
    }

    private fun item(name: String, bytes: ByteArray, size: Long = bytes.size.toLong()) = FileFrames.Item(name, size, "x/y", bytes.inputStream())

    private fun <T> loop(purpose: String, phone: (BulkPipe) -> T, mac: (BulkPipe) -> Unit): T {
        val okm = ByteArray(64) { 7 }
        val token = ByteArray(16) { it.toByte() }
        java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { srv ->
            var err: Throwable? = null
            val t = Thread {
                try {
                    srv.accept().use { s ->
                        java.io.DataInputStream(s.getInputStream()).readFully(ByteArray(16))
                        val k = BulkCrypto.keys(okm, token, purpose)
                        val pipe = BulkPipe(s, BulkFrame(LinkSession(k.copyOfRange(0, 32), k.copyOfRange(32, 64), LinkSession.MAC_TO_PHONE, LinkSession.PHONE_TO_MAC, aad = BulkCrypto.AAD)), purpose, null)
                        mac(pipe)
                    }
                } catch (e: Throwable) {
                    err = e
                }
            }.apply { start() }
            val sock = java.net.Socket("127.0.0.1", srv.localPort).apply { getOutputStream().write(token); soTimeout = 3000 }
            val r = BulkPipe(sock, BulkFrame(BulkCrypto.phone(okm, token, purpose)), purpose, null).use(phone)
            t.join(3000)
            err?.let { throw it }
            return r
        }
    }

    @Test
    fun `phone receives files from a mac stand-in and replies with the count`() {
        val big = ByteArray(150_000) { (it % 251).toByte() }
        val sink = Mem()
        val n = loop("files_in", { FileFrames.receive(it, sink) }) { m ->
            FileFrames.send(m, listOf(item("a.bin", big), item("../../b", ByteArray(0))))
        }
        assertEquals(2, n)
        assertEquals(listOf("begin a.bin", "finish", "begin ../../b", "finish"), sink.log)
        assertContentEquals(big, sink.files[0].second.toByteArray())
    }

    @Test
    fun `send streams chunks within the frame limit and returns the mac count`() {
        val big = ByteArray(200_000) { it.toByte() }
        val sizes = mutableListOf<Int>()
        val metas = mutableListOf<Map<String, Any?>>()
        val n = loop("files_out", { FileFrames.send(it, listOf(item("a.txt", big), item("z", ByteArray(3)))) }) { m ->
            while (true) {
                val (t, b) = m.read()
                if (t == BulkFrame.DATA) sizes += b.size else Json.obj(b.decodeToString()).also { j ->
                    if (j["t"] == "end") return@loop m.sendJson(mapOf("t" to "end", "saved" to 2)) else metas += j
                }
            }
        }
        assertEquals(2, n)
        assertEquals(listOf("a.txt", "z"), metas.map { it["name"] })
        assertEquals(200_000L, (metas[0]["size"] as Number).toLong())
        assertEquals("x/y", metas[0]["mime"])
        assertEquals(200_003, sizes.sum())
        assertTrue(sizes.all { it <= FileFrames.CHUNK })
    }

    @Test
    fun `link drop mid file aborts the pending file`() {
        val sink = Mem()
        assertFailsWith<Exception> {
            loop("files_in", { FileFrames.receive(it, sink) }) { m ->
                m.sendJson(mapOf("t" to "file", "name" to "big", "size" to 10L, "mime" to "x/y"))
                m.send(BulkFrame.DATA, ByteArray(3))
            }
        }
        assertEquals(listOf("begin big", "abort"), sink.log)
    }

    @Test
    fun `size mismatch aborts and never finishes`() {
        val sink = Mem()
        assertFailsWith<LinkError> {
            loop("files_in", { FileFrames.receive(it, sink) }) { m ->
                m.sendJson(mapOf("t" to "file", "name" to "a", "size" to 10L, "mime" to "x/y"))
                m.send(BulkFrame.DATA, ByteArray(3))
                m.sendJson(mapOf("t" to "end"))
            }
        }
        assertEquals(listOf("begin a", "abort"), sink.log)
        val over = Mem()
        assertFailsWith<LinkError> {
            loop("files_in", { FileFrames.receive(it, over) }) { m ->
                m.sendJson(mapOf("t" to "file", "name" to "a", "size" to 2L, "mime" to "x/y"))
                m.send(BulkFrame.DATA, ByteArray(3))
            }
        }
        assertEquals(listOf("begin a", "abort"), over.log)
    }

    @Test
    fun `data before a file and unknown frames fail closed`() {
        assertFailsWith<LinkError> { loop("files_in", { FileFrames.receive(it, Mem()) }) { it.send(BulkFrame.DATA, ByteArray(1)) } }
        assertFailsWith<LinkError> { loop("files_in", { FileFrames.receive(it, Mem()) }) { it.sendJson(mapOf("t" to "nope")) } }
    }

    @Test
    fun `sender fails when a source is shorter than declared and closes every item`() {
        val closed = mutableListOf<String>()
        val short = FileFrames.Item("s", 10, "x/y", object : java.io.ByteArrayInputStream(ByteArray(4)) { override fun close() { closed += "s" } })
        val spare = FileFrames.Item("t", 1, "x/y", object : java.io.ByteArrayInputStream(ByteArray(1)) { override fun close() { closed += "t" } })
        assertFailsWith<LinkError> {
            loop("files_out", { FileFrames.send(it, listOf(short, spare)) }) { m -> runCatching { while (true) m.read() } }
        }
        assertEquals(setOf("s", "t"), closed.toSet())
    }
}
