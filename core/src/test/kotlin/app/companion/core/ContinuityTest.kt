package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
}
