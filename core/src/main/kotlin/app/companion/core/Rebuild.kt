package app.companion.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object Rebuild {
    fun of(raw: Raw, e: Event, got: Map<Field, String>, zone: ZoneId = ZoneId.systemDefault()): Event {
        if (got.isEmpty() || e is Event.Otp) return e
        val t = raw.text()
        got[Field.OtpCode]?.trim()?.takeIf { Otp.fits(t, it) }?.let { return Otp.make(raw, t, it) }
        val ref = Instant.ofEpochMilli(raw.at).atZone(zone).toLocalDate()
        var x = e
        got[Field.Amount]?.let(Verbatim::paise)?.let { p -> x = amount(x, raw, t, ref, p) }
        got[Field.Merchant]?.let(Txn::name)?.let { x = merchant(x, it) }
        got[Field.Due]?.let { Dates.first(it, ref) }?.let { d -> x = due(x, d) }
        return x
    }

    private fun amount(e: Event, r: Raw, t: String, ref: LocalDate, paise: Long): Event {
        if (e != Event.Alert && e != Event.Unknown) return e
        val a = Amt(paise, "INR", 0, 0)
        return Bill.parse(r, t, ref, a) ?: Txn.parse(r, t, a) ?: e
    }

    private fun merchant(e: Event, m: String): Event = when (e) {
        is Event.Debit -> if (e.merchant == null) e.copy(merchant = m) else e
        is Event.Credit -> if (e.merchant == null) e.copy(merchant = m) else e
        is Event.CardSpend -> if (e.merchant == null) e.copy(merchant = m) else e
        is Event.Bill -> if (e.biller == null) e.copy(biller = m) else e
        else -> e
    }

    private fun due(e: Event, d: LocalDate): Event = when (e) {
        is Event.Bill -> if (e.due == null) e.copy(due = d) else e
        is Event.Statement -> if (e.due == null) e.copy(due = d) else e
        else -> e
    }
}
