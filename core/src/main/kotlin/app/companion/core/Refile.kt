package app.companion.core

data class Filed(val kind: String, val tags: String?, val category: String?, val state: String)

object Refile {
    const val ASK = "ask"
    const val CHECK = "check"
    const val SETTLED = "settled"
    const val PAID = "paid"
    const val SOURCES = "'Sms', 'Wa', 'Ig'"
    val SOURCES_SET = setOf("Sms", "Wa", "Ig")

    private val money = setOf(Kind.Debit.name, Kind.Credit.name, Kind.CardSpend.name)

    fun dismissed(old: Filed, named: Boolean) =
        old.state == SETTLED && (old.kind == Kind.Personal.name || (old.kind in money && named && old.category == Category.Other.label))

    fun locked(old: Filed, named: Boolean, corrected: Boolean, ruled: Boolean) =
        corrected || ruled || old.state == PAID || dismissed(old, named)

    fun usable(v: Verdict) = v is Verdict.Sure || v.guess != null

    private fun otp(f: Filed) = f.kind == Kind.Otp.name || f.tags?.split(',')?.contains("otp") == true

    fun moved(old: Filed, new: Filed) = new != old && otp(old) == otp(new)

    fun lost(old: Filed, new: Filed) = otp(old) && !otp(new)

    fun asks(old: Filed, new: Filed) = moved(old, new) && new.state == ASK && old.state != ASK

    fun summary(moved: Int, ask: Int, skip: Int) = buildList {
        add(if (moved == 0) "Nothing changed" else "$moved refiled")
        if (moved > 0 && ask > 0) add("$ask now need you")
        if (skip > 0) add("$skip skipped")
    }.joinToString(", ")

    fun imported(read: Int, added: Int, ask: Int) = buildList {
        add("Read $read ${if (read == 1) "message" else "messages"}")
        add("$added new")
        if (ask > 0) add("$ask need you")
    }.joinToString(", ")
}
