package app.companion.core

import java.time.LocalDate

internal object Misc {
    private val stages = listOf(
        Regex("(?i)out for delivery|arriving today|will be delivered today") to Stage.Out,
        Regex("(?i)\\bdelivered\\b") to Stage.Delivered,
        Regex("(?i)shipped|dispatched|on its way|in transit") to Stage.Shipped,
        Regex("(?i)order\\b.{0,30}(?:placed|confirmed)|awb|tracking") to Stage.Placed,
    )
    private val trips = listOf(
        Regex("(?i)\\bflight\\b|boarding|web check-in|pnr.{0,40}(?:air|flight)") to "Flight",
        Regex("(?i)\\birctc\\b|\\btrain\\b|\\bpnr\\b") to "Train",
        Regex("(?i)\\bbus\\b.{0,20}(?:ticket|booking)|redbus") to "Bus",
        Regex("(?i)hotel|check-in at|booking confirmed|reservation") to "Stay",
    )
    private val promo = Regex(
        "(?i)\\b\\d+%\\s*off|\\bflat\\s+(?:rs|₹)|\\bsale\\b|\\boffers?\\b|\\bdeals?\\b|discount|coupon|voucher|\\bwin\\b|hurry|limited\\s+time|apply\\s+now|pre-?approved|click\\s+(?:here|http)|\\bt&c|\\btnc\\b|unsubscribe|reply\\s+stop|use\\s+code|\\bcashback\\s+up\\s+to",
    )
    private val receipt = Regex("(?i)receipt|invoice|payment\\s+(?:successful|confirmation)|you\\s+paid|order\\s+(?:confirmation|summary)")

    fun delivery(r: Raw, t: String): Event.Delivery? {
        val stage = stages.firstOrNull { it.first.containsMatchIn(t) }?.second ?: return null
        if (!Regex("(?i)order|delivery|shipment|package|parcel|awb|courier").containsMatchIn(t)) return null
        return Event.Delivery(Brands.shop(r.sender, t), stage)
    }

    fun travel(t: String, ref: LocalDate): Event.Travel? {
        val what = trips.firstOrNull { it.first.containsMatchIn(t) }?.second ?: return null
        return Event.Travel(what, Dates.first(t, ref))
    }

    fun promo(t: String) = promo.containsMatchIn(t)

    fun receipt(r: Raw, t: String): Event.Debit? {
        if (r.source != Source.Mail || !receipt.containsMatchIn(t)) return null
        val a = Money.txn(t) ?: return null
        return Event.Debit(a.paise, a.currency, Txn.last4(t), Brands.bank(r.sender, t), Brands.shop(r.sender, t), Mode.Other)
    }
}
