package app.companion.core

import java.time.LocalDate

internal object Misc {
    private val stages = listOf(
        Regex("(?i)out for delivery|arriving|will (?:deliver|be delivered)\\b[^.]{0,30}today|delivery attempt|tried reaching|rejected|delivery failed") to Stage.Out,
        Regex("(?i)\\bdelivered\\b") to Stage.Delivered,
        Regex("(?i)shipped|dispatched|on its way|in transit|picked up|order picked|sent by|resent") to Stage.Shipped,
        Regex("(?i)order\\b.{0,30}(?:placed|confirmed)|awb|tracking") to Stage.Placed,
    )
    private val about = Regex("(?i)order|delivery|shipment|package|parcel|awb|courier")
    private val off = Regex("(?i)refund|\\breturn\\b|appointment|policy")
    private val trips = listOf(
        Regex("(?i)\\bflight\\b|boarding|web check-in|pnr.{0,40}(?:air|flight)") to "Flight",
        Regex("(?i)\\birctc\\b|\\btrain\\b|\\bpnr\\b") to "Train",
        Regex("(?i)\\bbus\\b.{0,20}(?:ticket|booking)|redbus") to "Bus",
        Regex("(?i)hotel|check-in at|booking confirmed|reservation") to "Stay",
    )
    private val promo = Regex(
        "(?i)\\b\\d+%\\s*off|\\bflat\\s+(?:rs|₹)|\\bsale\\b|\\boffers?\\b|\\bdeals?\\b|discount|coupon|voucher|\\bwin\\b|hurry|limited\\s+time|apply\\s+now|pre-?approved|click\\s+(?:here|http)|\\bt&c|\\btnc\\b|unsubscribe|reply\\s+stop|use\\s+code|\\bcashback\\s+up\\s+to|\\b(?:get|win|earn|claim|avail)\\b[^.]{0,30}\\bcashback\\b",
    )
    private val alert = Regex(
        "(?i)\\b(?:log(?:ged)?[\\s-]?in|sign(?:ed)?[\\s-]?in|new device|password|security|fraud|scam|beware|phishing|suspicious|unauthori[sz]ed|safety|kyc|aadhaar|appointment|booking|reservation|registered|registration|activat\\w+|welcome|application|blocked|alert|notice|reminder|verif\\w+|feedback|survey|nomination|complaint|service request|work order|installation|policy)\\b",
    )
    private val secure = Regex("(?i)log(?:ged)?[\\s-]?in|sign(?:ed)?[\\s-]?in|new device|password|security alert|fraud|scam|beware|suspicious|unauthori[sz]ed")
    private val svc = setOf("S", "T", "G")
    private val suffix = Regex("-([A-Za-z])$")
    private val receipt = Regex("(?i)receipt|invoice|payment\\s+(?:successful|confirmation)|you\\s+paid|order\\s+(?:confirmation|summary)")

    fun stage(t: String) = stages.firstOrNull { it.first.containsMatchIn(t) }?.second

    fun delivery(r: Raw, t: String): Event.Delivery? {
        val stage = stage(t) ?: return null
        if (!about.containsMatchIn(t) || off.containsMatchIn(t)) return null
        return Event.Delivery(Brands.shop(r.sender, t), stage)
    }

    fun travel(t: String, ref: LocalDate): Event.Travel? {
        val what = trips.firstOrNull { it.first.containsMatchIn(t) }?.second ?: return null
        return Event.Travel(what, Dates.first(t, ref))
    }

    private fun tag(sender: String) = suffix.find(sender)?.groupValues?.get(1)?.uppercase()

    fun promoSender(sender: String) = tag(sender) == "P"

    fun service(sender: String) = tag(sender) in svc

    fun promo(sender: String, t: String) = when (tag(sender)) {
        "P" -> true
        null -> promo.containsMatchIn(t) && !secure.containsMatchIn(t)
        else -> false
    }

    fun alert(t: String) = alert.containsMatchIn(t)

    fun receipt(r: Raw, t: String): Event.Debit? {
        if (r.source != Source.Mail || !receipt.containsMatchIn(t)) return null
        val a = Money.txn(t) ?: return null
        return Event.Debit(a.paise, a.currency, Txn.last4(t), Brands.bank(r.sender, t), Brands.shop(r.sender, t), Mode.Other)
    }
}
