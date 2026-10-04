package app.companion.core

import java.time.LocalDate

enum class Source { Sms, Notif, Mail, Wa, Ig }

enum class Mode { Upi, Card, Netbanking, Other }

enum class Kind { Otp, Debit, Credit, CardSpend, Bill, Statement, Delivery, Travel, Alert, Promo, Personal, Unknown }

enum class Stage { Placed, Shipped, Out, Delivered }

data class Raw(val source: Source, val sender: String, val title: String, val body: String, val at: Long)

sealed interface Event {
    val kind: Kind

    data class Otp(val code: String, val expiresAt: Long, val service: String?, val purpose: String?) : Event {
        override val kind get() = Kind.Otp
    }

    sealed interface Move : Event {
        val paise: Long
        val currency: String
        val last4: String?
        val bank: String?
        val merchant: String?
        val mode: Mode
    }

    data class Debit(
        override val paise: Long,
        override val currency: String,
        override val last4: String?,
        override val bank: String?,
        override val merchant: String?,
        override val mode: Mode,
    ) : Move {
        override val kind get() = Kind.Debit
    }

    data class Credit(
        override val paise: Long,
        override val currency: String,
        override val last4: String?,
        override val bank: String?,
        override val merchant: String?,
        override val mode: Mode,
    ) : Move {
        override val kind get() = Kind.Credit
    }

    data class CardSpend(
        override val paise: Long,
        override val currency: String,
        override val last4: String?,
        override val bank: String?,
        override val merchant: String?,
    ) : Move {
        override val mode get() = Mode.Card
        override val kind get() = Kind.CardSpend
    }

    data class Bill(
        val paise: Long,
        val currency: String,
        val due: LocalDate?,
        val minPaise: Long?,
        val biller: String?,
        val last4: String?,
    ) : Event {
        override val kind get() = Kind.Bill
    }

    data class Statement(
        val paise: Long,
        val minPaise: Long?,
        val due: LocalDate?,
        val last4: String?,
        val bank: String?,
    ) : Event {
        override val kind get() = Kind.Statement
    }

    data class Delivery(val merchant: String?, val stage: Stage) : Event {
        override val kind get() = Kind.Delivery
    }

    data class Travel(val what: String, val date: LocalDate?) : Event {
        override val kind get() = Kind.Travel
    }

    data object Alert : Event {
        override val kind get() = Kind.Alert
    }

    data object Promo : Event {
        override val kind get() = Kind.Promo
    }

    data object Personal : Event {
        override val kind get() = Kind.Personal
    }

    data object Unknown : Event {
        override val kind get() = Kind.Unknown
    }
}
