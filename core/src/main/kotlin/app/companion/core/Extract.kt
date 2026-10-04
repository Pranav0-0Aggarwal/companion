package app.companion.core

import java.time.Instant
import java.time.ZoneId

object Extract {
    private val moved = Regex("(?i)debited|credited|spent|withdrawn|\\bpaid\\b|received|refund")

    fun from(r: Raw, zone: ZoneId = ZoneId.systemDefault()): Event {
        val t = listOf(r.title, r.body).filter { it.isNotBlank() }.joinToString("\n")
        val ref = Instant.ofEpochMilli(r.at).atZone(zone).toLocalDate()
        if (r.source == Source.Wa || r.source == Source.Ig) return Event.Personal
        Otp.parse(r, t)?.let { return it }
        val promo = Misc.promo(t) && !moved.containsMatchIn(t)
        if (!promo) {
            Bill.parse(r, t, ref)?.let { return it }
            Txn.parse(r, t)?.let { return it }
            Misc.receipt(r, t)?.let { return it }
        }
        Misc.delivery(r, t)?.let { return it }
        Misc.travel(t, ref)?.let { return it }
        return when {
            promo -> Event.Promo
            r.source == Source.Sms && r.sender.any { it.isLetter() } -> Event.Unknown
            r.source == Source.Mail || r.source == Source.Notif -> Event.Unknown
            else -> Event.Personal
        }
    }
}
