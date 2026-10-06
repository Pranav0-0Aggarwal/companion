package app.companion.core

object Tidy {
    private const val DAY = 86_400_000L

    fun of(f: Filed, money: Boolean, merchant: String?, credit: Boolean, at: Long, now: Long): Filed? {
        val age = now - at
        return when {
            f.state == Refile.CHECK -> f.copy(state = Refile.SETTLED).takeIf { age > 3 * DAY }
            f.state != Refile.ASK -> null
            money -> Category.of(merchant).takeIf { merchant != null && !credit && it != Category.Other }?.let { f.copy(category = it.label, state = Refile.SETTLED) }
            age > 14 * DAY -> f.copy(kind = if (f.kind == Kind.Unknown.name) Kind.Alert.name else f.kind, state = Refile.SETTLED)
            else -> null
        }
    }
}
