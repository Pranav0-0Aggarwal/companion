package app.companion.core

import java.net.URLDecoder
import java.net.URI
import java.security.MessageDigest

class LinkPair(val mac: ByteArray, val secret: ByteArray, val name: String) {
    val code get() = LinkMsg.code(mac, secret)
}

object LinkMsg {
    const val PLAIN: Byte = 0
    val share = listOf("otp", "money", "alerts", "clipIn", "clipOut", "ask", "battery", "tasks", "notifs")

    fun plain(json: String) = byteArrayOf(PLAIN) + json.encodeToByteArray()

    fun shares(on: Set<String>): Map<String, Boolean> = share.associateWith { it in on }

    fun hello(np: ByteArray, pk: ByteArray) = mapOf("t" to "hello", "v" to 1, "np" to LinkCrypto.b64(np), "pk" to LinkCrypto.b64(pk))

    fun ok(name: String, on: Set<String>) = mapOf("t" to "ok", "name" to name, "share" to shares(on))

    fun shareEvent(on: Set<String>) = mapOf("t" to "share", "share" to shares(on))

    fun otpGone(item: Long) = mapOf("t" to "otp_gone", "item" to item)

    fun battery(pct: Int, charging: Boolean) = mapOf("t" to "battery", "pct" to pct, "charging" to charging)

    fun clip(text: String) = mapOf("t" to "clip", "text" to text)

    fun dirty(what: String) = mapOf("t" to "dirty", "what" to what)

    fun notif(id: String, app: String, appName: String, title: String, text: String, at: Long) =
        mapOf("t" to "notif", "id" to id, "app" to app, "appName" to appName, "title" to title, "text" to text, "at" to at)

    fun code(k: ByteArray, s: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(k + s)
        val n = (d[0].toInt() and 0xFF shl 16) or (d[1].toInt() and 0xFF shl 8) or (d[2].toInt() and 0xFF)
        return "%06d".format(n % 1_000_000)
    }

    fun parse(url: String): LinkPair? = runCatching {
        val u = URI(url)
        if (u.scheme != "companion" || u.host != "pair") return null
        val q = u.rawQuery.orEmpty().split('&').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to URLDecoder.decode(it[1], Charsets.UTF_8) } }.toMap()
        val k = LinkCrypto.unb64(q.getValue("k"))
        val s = LinkCrypto.unb64(q.getValue("s"))
        if (q["v"] != "1" || k.size != 32 || s.size != 32) return null
        LinkPair(k, s, q["n"].orEmpty().filter { !it.isISOControl() }.trim().take(NAME).ifEmpty { "Mac" })
    }.getOrNull()

    private const val NAME = 40
}
