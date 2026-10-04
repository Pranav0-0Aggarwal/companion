package app.companion.core

import java.security.MessageDigest

object Template {
    private val num = Regex("\\d[\\d,.]*")

    fun of(r: Raw): String {
        val text = r.title + "\n" + r.body
        val brand = Fingerprint.norm(Brands.service(r.sender, text) ?: Brands.clean(r.sender)).orEmpty()
        val body = Fingerprint.norm(num.replace(text, "0")).orEmpty()
        return MessageDigest.getInstance("SHA-256").digest("$brand|$body".toByteArray()).take(8).joinToString("") { "%02x".format(it) }
    }
}
