package app.companion.core

import java.security.MessageDigest

object Template {
    private const val STOP = "on|via|ref|upi|for|is|has|was|using|avl|bal"
    private const val MONTH = "jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?|" +
        "mon(?:day)?|tue(?:s(?:day)?)?|wed(?:nesday)?|thu(?:r(?:s(?:day)?)?)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?"
    private const val WORD = "(?!(?:$STOP)\\b)[^\\s.,;:()\\[\\]|!?]+"
    private val i = RegexOption.IGNORE_CASE
    private val url = Regex("(?:https?://|www\\.)\\S+|\\b[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.[a-z]{2,6}/\\S+", i)
    private val id = Regex("[a-z0-9._%+-]+@[a-z0-9.-]+", i)
    private val ref = Regex("(?<![a-z0-9])(?!(?:rs|inr)\\d)(?=[a-z0-9]*\\d)(?=[a-z0-9]*[a-z])[a-z0-9]{5,}(?![a-z0-9])", i)
    private val month = Regex("\\b(?:$MONTH)\\b", i)
    private val num = Regex("\\d[\\d,.:/-]*")
    private val name = Regex("\\b(to|from|at|by|towards|info|vpa)\\b[ \\t:/-]*($WORD(?:[ \\t]+$WORD){0,5})", i)

    fun mask(text: String) = name.replace(
        num.replace(month.replace(ref.replace(id.replace(url.replace(text.replace("[redacted]", "0"), "<u>"), "<id>"), "<ref>"), "<m>"), "0"),
    ) { "${it.groupValues[1]} <n>" }

    fun of(r: Raw): String {
        val text = r.title + "\n" + r.body
        val brand = Fingerprint.norm(Brands.service(r.sender, text) ?: Brands.clean(r.sender)).orEmpty()
        val body = Fingerprint.norm(mask(text.lowercase())).orEmpty()
        return MessageDigest.getInstance("SHA-256").digest("2:$brand|$body".toByteArray()).take(8).joinToString("") { "%02x".format(it) }
    }

    fun brand(sender: String, text: String) = Brands.service(sender, text) ?: Brands.clean(sender)
}
