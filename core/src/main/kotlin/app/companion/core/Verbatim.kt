package app.companion.core

import java.math.BigDecimal
import java.time.LocalDate

object Verbatim {
    private val sign = Regex("(?i)rs\\.?|inr|₹|usd|\\$|eur|€|gbp|£|[\\s,]")
    private val plain = Regex("\\d+(?:\\.\\d+)?")
    private val num = Regex("\\d[\\d,]*(?:\\.\\d+)?")
    private val prefix = Regex("(?i)(?<![a-z])(?:rs|inr|usd|eur|gbp)$")
    private val space = Regex("\\s+")
    private val epoch = LocalDate.of(2000, 1, 1)

    internal fun code(digits: String) = Regex("(?<![\\p{L}\\d])(?<!\\d[.,-])$digits(?![\\p{L}\\d])(?![.,-]\\d)")

    private fun standalone(text: String, at: Int) = !text.getOrElse(at - 1) { ' ' }.isLetter() || prefix.containsMatchIn(text.substring(0, at))

    private fun norm(s: String) = space.replace(s.trim(), " ")

    private fun number(s: String) = sign.replace(s, "").takeIf { plain.matches(it) }?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

    fun paise(value: String): Long? = number(value)?.let { runCatching { it.movePointRight(2).toBigIntegerExact().longValueExact() }.getOrNull() }?.takeIf { it > 0 }

    fun ok(field: Field, value: String, text: String): Boolean = when (field) {
        Field.OtpCode -> value.trim().let { it.length in 4..8 && it.all(Char::isDigit) && code(it).containsMatchIn(text) }
        Field.Amount -> number(value)?.let { n -> num.findAll(text).any { m -> standalone(text, m.range.first) && m.value.replace(",", "").toBigDecimalOrNull()?.compareTo(n) == 0 } } == true
        Field.Merchant -> norm(value).let { it.length >= 2 && it.any(Char::isLetter) && norm(text).contains(it, ignoreCase = true) }
        Field.Due -> norm(value).let { it.isNotEmpty() && norm(text).contains(it, ignoreCase = true) && Dates.first(it, epoch) != null }
    }
}
