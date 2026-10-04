package app.companion.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

val IST: ZoneId = ZoneId.of("Asia/Kolkata")
val NOW: Long = LocalDateTime.of(2026, 10, 4, 13, 30).atZone(IST).toInstant().toEpochMilli()

fun sms(sender: String, body: String, at: Long = NOW) = Raw(Source.Sms, sender, "", body, at)

fun notif(sender: String, title: String, body: String, at: Long = NOW) = Raw(Source.Notif, sender, title, body, at)

fun mail(sender: String, subject: String, body: String, at: Long = NOW) = Raw(Source.Mail, sender, subject, body, at)

fun extract(r: Raw): Event = Extract.from(r, IST)

fun day(y: Int, m: Int, d: Int): LocalDate = LocalDate.of(y, m, d)
