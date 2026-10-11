package app.companion.core

import java.security.MessageDigest

object Voip {
    val pkgs = setOf("com.whatsapp", "com.whatsapp.w4b")

    private val yes = Regex("answer|accept", RegexOption.IGNORE_CASE)
    private val no = Regex("decline|reject|dismiss|hang ?up", RegexOption.IGNORE_CASE)

    fun id(key: String) = MessageDigest.getInstance("SHA-256").digest(key.encodeToByteArray()).take(6).joinToString("") { "%02x".format(it) }

    fun state(fullScreen: Boolean, chrono: Boolean, titles: List<String>) =
        if (!chrono && (fullScreen || titles.any(yes::containsMatchIn))) "ringing" else "active"

    fun decline(titles: List<String>): Int? {
        titles.indexOfFirst(no::containsMatchIn).let { if (it >= 0) return it }
        val a = titles.indexOfFirst(yes::containsMatchIn)
        return if (titles.size == 2 && a >= 0) 1 - a else null
    }
}
