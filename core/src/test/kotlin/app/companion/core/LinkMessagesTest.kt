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

    @Test
    fun `v2 events match the fixture`() {
        val ev = sec("ev")
        assertEquals(ev["bulk_want"], norm(LinkMsg.bulkWant("files_out", "r1")))
        assertEquals(ev["bridge"], norm(LinkMsg.bridge(true)))
        assertEquals(ev["bridge_want"], norm(LinkMsg.bridgeWant("192.168.1.20", "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")))
        assertEquals(ev["call"], norm(LinkMsg.call("c1", "ringing", "+919812345678", "Riya", null, 1_760_000_000_000, "full")))
        assertEquals(ev["call_audio"], norm(LinkMsg.callAudio("mac")))
        assertEquals(ev["notif_v2"], norm(LinkMsg.notif("0|com.whatsapp|1|null|10123", "com.whatsapp", "WhatsApp", "Riya", "Are you free tonight?", 1_760_000_000_000, "0|com.whatsapp|1|null|10123", "com.whatsapp|riya@s.whatsapp.net", true, listOf(mapOf("id" to "0", "title" to "Mark as read")), mapOf("name" to "Riya"))))
        assertEquals(ev["live"], norm(LinkMsg.live("0|in.swiggy.android|7|null|10200", "in.swiggy.android", "Swiggy", "Order on the way", "Arriving in 12 min", 60, 100, 1_760_000_720_000, null, null)))
        assertEquals(ev["live_gone"], norm(LinkMsg.liveGone("0|in.swiggy.android|7|null|10200")))
        assertEquals(ev["media"], norm(LinkMsg.media("com.spotify.music", "Song", "Artist", null, true, 61_000, 200_000, 1_760_000_000_000)))
        assertEquals(ev["shot"], norm(LinkMsg.shot(1_760_000_000_123, 1080, 2340, "/9j/4AAQSkZJRg==", "image/png")))
        assertEquals(ev["clip_img"], norm(LinkMsg.clipImg("k1", 1080, 720, "/9j/4AAQSkZJRg==")))
        assertEquals(ev["handoff"], norm(LinkMsg.handoff("https://example.com/page", "Example page")))
        assertEquals(ev["dnd"], norm(LinkMsg.dnd(true)))
        assertEquals(ev["ok_v2"], norm(LinkMsg.ok("Galaxy Z Flip7", LinkMsg.share.toSet() - setOf("clipIn", "clipOut", "notifs", "photos", "clipImage"))))
    }

    @Test
    fun `v2 share keys and proto`() {
        assertEquals(listOf("calls", "live", "files", "photos", "clipImage", "handoff", "dnd", "mirror"), LinkMsg.share.drop(9))
        assertEquals(2L, sec("hello").getValue("ok")["proto"])
    }
}
