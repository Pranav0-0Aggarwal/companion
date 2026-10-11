package app.companion.core

import java.net.URLDecoder
import java.net.URI
import java.security.MessageDigest

class LinkPair(val mac: ByteArray, val secret: ByteArray, val name: String) {
    val code get() = LinkMsg.code(mac, secret)
}

object LinkMsg {
    const val PLAIN: Byte = 0
    val share = listOf("otp", "money", "alerts", "clipIn", "clipOut", "ask", "battery", "tasks", "notifs", "calls", "live", "files", "photos", "clipImage", "handoff", "dnd", "mirror")

    fun plain(json: String) = byteArrayOf(PLAIN) + json.encodeToByteArray()

    fun shares(on: Set<String>): Map<String, Boolean> = share.associateWith { it in on }

    fun hello(np: ByteArray, pk: ByteArray) = mapOf("t" to "hello", "v" to 1, "np" to LinkCrypto.b64(np), "pk" to LinkCrypto.b64(pk))

    fun ok(name: String, on: Set<String>) = mapOf("t" to "ok", "name" to name, "share" to shares(on), "proto" to 2)

    fun shareEvent(on: Set<String>) = mapOf("t" to "share", "share" to shares(on))

    fun otpGone(item: Long) = mapOf("t" to "otp_gone", "item" to item)

    fun battery(pct: Int, charging: Boolean) = mapOf("t" to "battery", "pct" to pct, "charging" to charging)

    fun clip(text: String) = mapOf("t" to "clip", "text" to text)

    fun dirty(what: String) = mapOf("t" to "dirty", "what" to what)

    fun notif(id: String, app: String, appName: String, title: String, text: String, at: Long) =
        mapOf("t" to "notif", "id" to id, "app" to app, "appName" to appName, "title" to title, "text" to text, "at" to at)

    fun bulkWant(purpose: String, ref: String) = mapOf("t" to "bulk_want", "purpose" to purpose, "ref" to ref)

    fun bridge(up: Boolean, why: String? = null) = mapOf("t" to "bridge", "up" to up) + (why?.let { mapOf("why" to it) } ?: emptyMap())

    fun bridgeWant(ip: String, k: String) = mapOf("t" to "bridge_want", "ip" to ip, "k" to k)

    fun call(id: String, state: String, number: String?, name: String?, photo: String?, started: Long?, ctl: String) =
        compact("t" to "call", "id" to id, "state" to state, "number" to number, "name" to name, "photo" to photo, "started" to started, "ctl" to ctl)

    fun voip(id: String, state: String, name: String?) = compact("t" to "voip", "id" to id, "app" to "WhatsApp", "state" to state, "name" to name)

    fun callAudio(where: String, why: String? = null) = compact("t" to "call_audio", "where" to where, "why" to why)

    fun notif(id: String, app: String, appName: String, title: String, text: String, at: Long, key: String, conv: String, canReply: Boolean, actions: List<Map<String, String>>, person: Map<String, String>?) =
        compact("t" to "notif", "id" to id, "app" to app, "appName" to appName, "title" to title, "text" to text, "at" to at, "key" to key, "conv" to conv, "canReply" to canReply, "actions" to actions, "person" to person)

    fun live(key: String, app: String, appName: String, title: String, text: String, progress: Int?, max: Int?, eta: Long?, chrono: Long?, icon: String?) =
        compact("t" to "live", "key" to key, "app" to app, "appName" to appName, "title" to title, "text" to text, "progress" to progress, "max" to max, "eta" to eta, "chrono" to chrono, "icon" to icon)

    fun liveGone(key: String) = mapOf("t" to "live_gone", "key" to key)

    fun media(pkg: String, title: String, artist: String, art: String?, playing: Boolean, pos: Long, dur: Long, at: Long) =
        compact("t" to "media", "pkg" to pkg, "title" to title, "artist" to artist, "art" to art, "playing" to playing, "pos" to pos, "dur" to dur, "at" to at)

    fun shot(id: Long, w: Int, h: Int, thumb: String, mime: String) = mapOf("t" to "shot", "id" to id, "w" to w, "h" to h, "thumb" to thumb, "mime" to mime)

    fun clipImg(ref: String, w: Int, h: Int, thumb: String) = mapOf("t" to "clip_img", "ref" to ref, "w" to w, "h" to h, "thumb" to thumb)

    fun handoff(url: String, title: String) = mapOf("t" to "handoff", "url" to url, "title" to title)

    fun dnd(on: Boolean) = mapOf("t" to "dnd", "on" to on)

    private fun compact(vararg p: Pair<String, Any?>) = p.filter { it.second != null }.toMap()

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
