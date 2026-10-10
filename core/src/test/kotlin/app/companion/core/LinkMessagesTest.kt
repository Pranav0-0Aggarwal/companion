package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinkMessagesTest {
    private val doc = Json.obj(checkNotNull(javaClass.getResourceAsStream("/link-messages.json")).use { it.readBytes().decodeToString() })

    @Suppress("UNCHECKED_CAST")
    private fun sec(k: String) = doc.getValue(k) as Map<String, Map<String, Any?>>

    private fun norm(o: Any?) = Json.parse(Json.write(o))

    @Test
    fun `every fixture message survives write and parse`() {
        for (k in listOf("hello", "req", "res", "ev")) sec(k).forEach { (n, m) -> assertEquals(m, norm(m), "$k.$n") }
    }

    @Test
    fun `requests carry an integer id and res echoes it`() {
        sec("req").forEach { (n, m) -> assertTrue(m["id"] is Long && m["t"] is String, n) }
        sec("res").forEach { (n, m) -> assertTrue(m["id"] is Long && m["ok"] is Boolean && (m["ok"] == true || m["err"] is String), n) }
    }

    @Test
    fun `handshake messages match the fixture`() {
        val h = sec("hello")
        val np = LinkCrypto.unb64(h.getValue("phone")["np"] as String)
        val pk = LinkCrypto.unb64(h.getValue("phone")["pk"] as String)
        assertEquals(h["phone"], norm(LinkMsg.hello(np, pk)))
        assertEquals(h["ok"], norm(LinkMsg.ok("Galaxy Z Flip7", setOf("otp", "money", "alerts", "ask", "battery", "tasks"))))
        assertEquals(16, LinkCrypto.unb64(h.getValue("mac")["nm"] as String).size)
        assertEquals(h.getValue("mac")["nm"], LinkCrypto.b64(LinkCrypto.unb64(h.getValue("mac")["nm"] as String)))
    }

    @Test
    fun `events match the fixture`() {
        val ev = sec("ev")
        val on = setOf("otp", "money", "alerts", "ask", "battery", "tasks")
        assertEquals(ev["share"], norm(LinkMsg.shareEvent(on)))
        assertEquals(ev["otp_gone"], norm(LinkMsg.otpGone(1)))
        assertEquals(ev["battery"], norm(LinkMsg.battery(80, true)))
        assertEquals(ev["clip"], norm(LinkMsg.clip("copied on the phone")))
        assertEquals(ev["dirty"], norm(LinkMsg.dirty("today")))
        assertEquals(ev["notif"], norm(LinkMsg.notif("0|com.whatsapp|1|null|10123", "com.whatsapp", "WhatsApp", "Riya", "Are you free tonight?", 1_760_000_000_000)))
    }
}
