package app.companion.core

import java.time.LocalDate

enum class Group { Out, In, Due, Code }

data class Fingerprint(
    val group: Group,
    val paise: Long,
    val last4: String?,
    val merchant: String?,
    val due: LocalDate?,
    val code: String?,
) {
    companion object {
        const val WINDOW = 10 * 60 * 1000L
        const val ECHO = 3 * 24 * 60 * 60 * 1000L

        fun of(e: Event): Fingerprint? = when (e) {
            is Event.Debit -> Fingerprint(Group.Out, e.paise, e.last4, norm(e.merchant), null, null)
            is Event.CardSpend -> Fingerprint(Group.Out, e.paise, e.last4, norm(e.merchant), null, null)
            is Event.Credit -> Fingerprint(Group.In, e.paise, e.last4, norm(e.merchant), null, null)
            is Event.Bill -> Fingerprint(Group.Due, e.paise, e.last4, norm(e.biller), e.due, null)
            is Event.Statement -> Fingerprint(Group.Due, e.paise, e.last4, norm(e.bank), e.due, null)
            is Event.Otp -> Fingerprint(Group.Code, 0, null, null, null, e.code)
            else -> null
        }

        fun norm(s: String?) = s?.lowercase()?.filter { it.isLetterOrDigit() }?.takeIf { it.isNotEmpty() }

        private fun alike(a: String?, b: String?) =
            a != null && b != null && (a == b || (minOf(a.length, b.length) >= 4 && (a.contains(b) || b.contains(a))))

        fun same(a: Fingerprint, atA: Long, b: Fingerprint, atB: Long): Boolean {
            if (a.group != b.group) return false
            return when (a.group) {
                Group.Code -> a.code == b.code && kotlin.math.abs(atA - atB) <= WINDOW
                Group.Due -> a.paise == b.paise && a.due == b.due && ident(a, b)
                else -> a.paise == b.paise && kotlin.math.abs(atA - atB) <= WINDOW && ident(a, b)
            }
        }

        fun echo(a: Fingerprint, atA: Long, srcA: String, fromA: String?, b: Fingerprint, atB: Long, srcB: String, fromB: String?): Boolean {
            if (a.group != b.group || a.paise != b.paise || a.group == Group.Code || a.group == Group.Due) return false
            if (kotlin.math.abs(atA - atB) > ECHO || srcA == "Sms" && srcB == "Sms") return false
            if (srcA != srcB) return ident(a, b)
            return fromA != null && fromA == fromB && (ident(a, b) || a.last4 == null && b.last4 == null && a.merchant == null && b.merchant == null)
        }

        private fun ident(a: Fingerprint, b: Fingerprint): Boolean {
            if (a.last4 != null && b.last4 != null) return a.last4 == b.last4
            return alike(a.merchant, b.merchant)
        }
    }
}
