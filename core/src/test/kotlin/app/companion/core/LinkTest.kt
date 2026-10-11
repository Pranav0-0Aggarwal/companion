package app.companion.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkTest {
    private val v = Json.obj(checkNotNull(javaClass.getResourceAsStream("/link-vectors.json")).use { it.readBytes().decodeToString() })
    private fun s(k: String) = v.getValue(k) as String
    private fun key(k: String) = hex(s(k))
    private fun hex(h: String) = ByteArray(h.length / 2) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private fun str(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun msg(k: String) = v.getValue(k) as Map<*, *>

    private fun mac() = LinkSession(key("kMacToPhone"), key("kPhoneToMac"), 1, 2)
    private fun phone() = LinkCrypto.session(key("phonePrivate"), key("macPublic"), key("S"), key("nm"), key("np"))

    @Test
    fun `public keys come from the private keys`() {
        assertEquals(s("macPublic"), str(LinkCrypto.pub(key("macPrivate"))))
        assertEquals(s("phonePublic"), str(LinkCrypto.pub(key("phonePrivate"))))
    }

    @Test
    fun `session keeps the okm`() {
        assertEquals(s("okm"), str(phone().okm))
        assertEquals(0, mac().okm.size)
    }

    @Test
    fun `both sides derive the same shared secret`() {
        assertEquals(s("shared"), str(LinkCrypto.shared(key("phonePrivate"), key("macPublic"))))
        assertEquals(s("shared"), str(LinkCrypto.shared(key("macPrivate"), key("phonePublic"))))
    }

    @Test
    fun `hkdf reproduces okm and the two keys`() {
        val okm = LinkCrypto.okm(key("shared"), key("S"), key("nm"), key("np"))
        assertEquals(s("okm"), str(okm))
        assertEquals(s("kMacToPhone"), str(okm.copyOfRange(0, 32)))
        assertEquals(s("kPhoneToMac"), str(okm.copyOfRange(32, 64)))
    }

    @Test
    fun `hkdf matches RFC 5869 case 1`() {
        val okm = LinkCrypto.hkdf(hex("0b".repeat(22)), hex("000102030405060708090a0b0c"), hex("f0f1f2f3f4f5f6f7f8f9"), 42)
        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865", str(okm))
    }

    @Test
    fun `sealed messages match the Mac byte for byte`() {
        assertEquals(msg("msg1")["sealedMessage"], str(mac().seal((msg("msg1")["plaintext"] as String).encodeToByteArray(), 0)))
        assertEquals(msg("msg2")["sealedMessage"], str(phone().seal((msg("msg2")["plaintext"] as String).encodeToByteArray(), 7)))
    }

    @Test
    fun `phone opens the Mac message and the Mac opens the phone message`() {
        assertEquals(msg("msg1")["plaintext"], phone().open(hex(msg("msg1")["sealedMessage"] as String)).decodeToString())
        assertEquals(msg("msg2")["plaintext"], mac().open(hex(msg("msg2")["sealedMessage"] as String)).decodeToString())
    }

    @Test
    fun `counters start at 0 and rise per message`() {
        val p = phone()
        val a = p.seal("a".encodeToByteArray())
        val b = p.seal("b".encodeToByteArray())
        assertEquals(0L, java.nio.ByteBuffer.wrap(a, 1, 8).long)
        assertEquals(1L, java.nio.ByteBuffer.wrap(b, 1, 8).long)
    }

    @Test
    fun `a replayed or older counter is rejected`() {
        val m = mac()
        val one = m.seal("1".encodeToByteArray())
        val two = m.seal("2".encodeToByteArray())
        val p = phone()
        p.open(two)
        assertFailsWith<LinkError> { p.open(two) }
        assertFailsWith<LinkError> { p.open(one) }
    }

    @Test
    fun `a forged message does not advance the counter`() {
        val m = mac()
        val ok = m.seal("x".encodeToByteArray())
        val bad = ok.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }
        val p = phone()
        assertFailsWith<LinkError> { p.open(bad) }
        assertEquals("x", p.open(ok).decodeToString())
    }

    @Test
    fun `wrong direction, tampered header and short input are rejected`() {
        val own = phone().seal("x".encodeToByteArray())
        assertFailsWith<LinkError> { phone().open(own) }
        val tag = hex(msg("msg1")["sealedMessage"] as String)
        assertFailsWith<LinkError> { phone().open(tag.copyOf().also { it[8] = 9 }) }
        assertFailsWith<LinkError> { phone().open(tag.copyOf().also { it[0] = 0 }) }
        assertFailsWith<LinkError> { phone().open(tag.copyOf(20)) }
    }

    @Test
    fun `random keys agree`() {
        val a = LinkCrypto.keys()
        val b = LinkCrypto.keys()
        assertContentEquals(LinkCrypto.shared(a.priv, b.pub), LinkCrypto.shared(b.priv, a.pub))
    }

    @Test
    fun `pair url parses and shows the confirm code`() {
        val p = checkNotNull(LinkMsg.parse(s("pairUrl")))
        assertEquals(s("macPublic"), str(p.mac))
        assertEquals(s("S"), str(p.secret))
        assertEquals("Pranav’s MacBook Pro", p.name)
        assertEquals(s("confirmCode"), p.code)
        assertTrue(s("pairUrl").contains("k=" + LinkCrypto.b64u(p.mac)))
        assertTrue(s("pairUrl").contains("s=" + LinkCrypto.b64u(p.secret)))
    }

    @Test
    fun `bad pair urls are refused`() {
        val k = LinkCrypto.b64u(ByteArray(32))
        listOf(
            "https://pair?v=1&k=$k&s=$k", "companion://other?v=1&k=$k&s=$k", "companion://pair?v=2&k=$k&s=$k", "companion://pair?v=1&k=$k",
            "companion://pair?v=1&k=${LinkCrypto.b64u(ByteArray(31))}&s=$k", "companion://pair?v=1&k=!!&s=$k", "not a url",
        ).forEach { assertNull(LinkMsg.parse(it), it) }
    }

    @Test
    fun `pair name is trimmed and capped`() {
        val k = LinkCrypto.b64u(ByteArray(32))
        assertEquals("Mac", LinkMsg.parse("companion://pair?v=1&k=$k&s=$k")?.name)
        assertEquals(40, LinkMsg.parse("companion://pair?v=1&k=$k&s=$k&n=${"a".repeat(90)}")?.name?.length)
        assertEquals("a b", LinkMsg.parse("companion://pair?v=1&k=$k&s=$k&n=%0Aa%20b%07")?.name)
    }

    @Test
    fun `base64 accepts both alphabets`() {
        val b = byteArrayOf(-5, -1, -2, 63, 62)
        assertContentEquals(b, LinkCrypto.unb64(LinkCrypto.b64(b)))
        assertContentEquals(b, LinkCrypto.unb64(LinkCrypto.b64u(b)))
    }

    @Test
    fun `a small message is one frame with both bits`() {
        val f = LinkFrames.split(byteArrayOf(1, 2, 3), 5, 19)
        assertEquals(1, f.size)
        assertEquals(0xC5, f[0][0].toInt() and 0xFF)
    }

    @Test
    fun `frames split by chunk size and join back`() {
        val m = ByteArray(100) { it.toByte() }
        val f = LinkFrames.split(m, 70, 19)
        assertEquals(6, f.size)
        assertEquals(0x80 or 6, f.first()[0].toInt() and 0xFF)
        assertEquals(0x40 or 6, f.last()[0].toInt() and 0xFF)
        assertEquals(6, f[2][0].toInt() and 0xFF)
        assertTrue(f.all { it.size <= 20 })
        val r = LinkReassembler()
        val out = f.mapNotNull { r.feed(it) }
        assertEquals(1, out.size)
        assertContentEquals(m, out[0])
    }

    @Test
    fun `an exact multiple of the chunk makes no empty frame`() {
        assertEquals(2, LinkFrames.split(ByteArray(38), 0, 19).size)
        assertEquals(1, LinkFrames.split(ByteArray(0), 0, 19).size)
    }

    @Test
    fun `a first frame mid message drops the partial`() {
        val r = LinkReassembler()
        assertNull(r.feed(byteArrayOf(0x80.toByte(), 1, 2)))
        val whole = r.feed(byteArrayOf(0xC1.toByte(), 9))
        assertContentEquals(byteArrayOf(9), whole)
    }

    @Test
    fun `middle frames without a start are ignored`() {
        val r = LinkReassembler()
        assertNull(r.feed(byteArrayOf(0x40, 1)))
        assertNull(r.feed(byteArrayOf(0x01, 1)))
        assertNull(r.feed(byteArrayOf()))
    }

    @Test
    fun `a message over 256 KiB is dropped`() {
        val r = LinkReassembler()
        val big = LinkFrames.split(ByteArray(LinkFrames.MAX + 1), 0, 512)
        assertTrue(big.mapNotNull { r.feed(it) }.isEmpty())
        assertContentEquals(byteArrayOf(7), r.feed(byteArrayOf(0xC0.toByte(), 7)))
    }

    @Test
    fun `the largest message fits`() {
        val r = LinkReassembler()
        val m = ByteArray(LinkFrames.MAX)
        assertEquals(LinkFrames.MAX, LinkFrames.split(m, 0, 509).mapNotNull { r.feed(it) }.single().size)
    }

    @Test
    fun `handshake messages are json after a zero byte`() {
        val b = LinkMsg.plain("""{"t":"hello","v":1}""")
        assertEquals(0, b[0].toInt())
        assertEquals("hello", Json.obj(b.decodeToString(1)).get("t"))
    }

    @Test
    fun `share map lists every category`() {
        val m = LinkMsg.shares(setOf("otp", "tasks"))
        assertEquals(LinkMsg.share, m.keys.toList())
        assertEquals(listOf("otp", "tasks"), m.filterValues { it }.keys.toList())
        assertEquals("""{"t":"ok","name":"Galaxy Z Flip7","share":{"otp":true,"money":false}}""", Json.write(mapOf("t" to "ok", "name" to "Galaxy Z Flip7", "share" to LinkMsg.shares(setOf("otp")).filterKeys { it in setOf("otp", "money") })))
    }

    @Test
    fun `json round trips the shapes the link sends`() {
        val m = mapOf("t" to "res", "id" to 7, "ok" to true, "data" to mapOf("txns" to listOf(mapOf("paise" to 45000L, "title" to "Swiggy ₹ \"q\"\n", "at" to 0L))))
        val back = Json.obj(Json.write(m))
        assertEquals(7L, back["id"])
        assertEquals("Swiggy ₹ \"q\"\n", ((back["data"] as Map<*, *>)["txns"] as List<*>).let { (it[0] as Map<*, *>)["title"] })
    }
}
