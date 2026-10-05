package app.companion.core

object Ref {
    private val form = Regex("(?i)^\\s*i?\\s*:?\\s*(\\d{1,12})\\s*$")

    fun item(id: Long) = "i:$id"

    fun id(s: String?): Long? = s?.let { form.matchEntire(it) }?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0 }
}

sealed interface Do {
    data class Pay(val id: Long) : Do
    data class File(val id: Long, val category: String) : Do
    data class Retype(val id: Long, val label: String) : Do
    data class Dup(val id: Long, val keep: Long) : Do
    data class Rename(val id: Long, val from: String, val to: String) : Do
    data class Moved(val id: Long, val merchant: String) : Do
    data class AddCard(val bank: String, val last4: String) : Do
    data class Budget(val paise: Long) : Do
    data class Dismiss(val id: Long) : Do
}

enum class Pend { Wait, Run, Done, Fail, Cancel }

data class Pending(val key: Long, val act: Do, val text: String, val at: Pend = Pend.Wait) {
    private fun go(from: Pend, to: Pend) = if (at == from) copy(at = to) else null

    fun confirm() = go(Pend.Wait, Pend.Run)

    fun cancel() = go(Pend.Wait, Pend.Cancel)

    fun finish(ok: Boolean) = go(Pend.Run, if (ok) Pend.Done else Pend.Fail)
}

class Held(private val cap: Int = 8) {
    private var n = 0L
    private val all = LinkedHashMap<Long, Pending>()

    @Synchronized
    fun add(act: Do, text: String): Pair<Pending, Boolean> {
        all.values.firstOrNull { it.at == Pend.Wait && it.act == act }?.let { return it to false }
        val p = Pending(++n, act, text)
        all[p.key] = p
        while (all.size > cap) all.remove(all.keys.first())
        return p to true
    }

    @Synchronized
    fun get(key: Long) = all[key]

    @Synchronized
    private fun step(key: Long, f: (Pending) -> Pending?) = all[key]?.let(f)?.also { all[key] = it }

    fun confirm(key: Long) = step(key, Pending::confirm)

    fun cancel(key: Long) = step(key, Pending::cancel)

    fun finish(key: Long, ok: Boolean) = step(key) { it.finish(ok) }

    @Synchronized
    fun clear() = all.clear()
}
