package app.companion.data

import app.companion.core.Event
import app.companion.core.Fingerprint
import app.companion.core.Group
import app.companion.core.Repeat
import app.companion.core.Raw
import app.companion.core.Verdict
import app.companion.core.Worth
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class Added(val item: Item, val fresh: Boolean)

class Repo(private val db: Db) {
    private val d = db.dao()
    val fresh = ConcurrentHashMap<Long, Long>()

    val profile: Flow<Profile> = d.profile().map { it ?: Profile() }
    val cards = d.cards()
    val money = d.money()
    val bills = d.bills()
    val asks = d.asks()
    val tasks = d.tasks()

    suspend fun profileNow() = d.profileNow() ?: Profile()

    suspend fun save(p: Profile) = d.save(p)

    suspend fun edit(f: (Profile) -> Profile) = d.save(f(profileNow()))

    suspend fun addCard(c: Card) = d.addCard(c)

    suspend fun deleteCard(id: Long) = d.deleteCard(id)

    fun otps(now: Long = System.currentTimeMillis()) = d.otps(now)

    suspend fun otpsNow(now: Long = System.currentTimeMillis()) = d.otpsNow(now)

    suspend fun billsNow() = d.billsNow()

    suspend fun item(id: Long) = d.item(id)

    fun inbox(since: Long) = d.inbox(since)

    fun links(ids: List<Long>) = d.links(ids)

    fun tally(fromDay: Long) = d.tally(fromDay)

    fun search(q: String): Flow<List<Item>> {
        val t = q.split(Regex("\\s+")).map { w -> w.filter(Char::isLetterOrDigit) }.filter { it.isNotEmpty() }
        return if (t.isEmpty()) flowOf(emptyList()) else d.search(t.joinToString(" ") { "$it*" })
    }

    suspend fun sweep(now: Long = System.currentTimeMillis()) = d.sweep(now)

    suspend fun file(id: Long, category: String) {
        val key = d.item(id)?.merchant?.let { Fingerprint.norm(it) }
        d.file(id, category)
        if (key != null) d.learn(Learned(key, category))
    }

    suspend fun dismiss(id: Long) = d.setState(id, State.SETTLED)

    suspend fun pay(id: Long) = d.setState(id, State.PAID)

    suspend fun ping(id: Long, bits: Int) = d.setPing(id, bits)

    fun next(now: Long = System.currentTimeMillis()) = d.nextTask(now)

    suspend fun pending(now: Long = System.currentTimeMillis()) = d.pending(now)

    suspend fun task(id: Long) = d.task(id)

    suspend fun allTasks() = d.allTasks()

    suspend fun saveTask(t: Task): Task {
        val s = t.copy(upd = System.currentTimeMillis())
        return if (t.id == 0L) s.copy(id = d.addTask(s)) else s.also { d.updateTask(it) }
    }

    suspend fun finish(id: Long): Task? {
        val t = d.task(id) ?: return null
        val now = System.currentTimeMillis()
        val again = t.remindAt?.let { Repeat.after(it, t.repeat, now, ZoneId.systemDefault()) }
        return saveTask(if (again != null) t.copy(remindAt = again) else t.copy(done = true))
    }

    suspend fun reopen(id: Long): Task? = d.task(id)?.let { saveTask(it.copy(done = false)) }

    suspend fun removeTask(id: Long) {
        val t = d.task(id) ?: return
        if (t.gid != null) d.updateTask(t.copy(gone = true, upd = System.currentTimeMillis())) else d.purgeTask(id)
    }

    suspend fun purgeTask(id: Long) = d.purgeTask(id)

    suspend fun add(raw: Raw, v: Verdict, p: Profile): Added? {
        val e = v.event
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(raw.at).atZone(zone).toLocalDate().toEpochDay()
        val folded = when {
            e == Event.Promo -> "Promo"
            e == Event.Unknown && v is Verdict.Sure -> "Other"
            e == Event.Personal && !Worth.dm(raw.sender, raw.title + " " + raw.body, p.vips) -> raw.source.name
            else -> null
        }
        if (folded != null) {
            d.count(day, folded)
            return null
        }
        val learned = (e as? Event.Move)?.merchant?.let { Fingerprint.norm(it) }?.let { d.learned(it) }
        val item = Items.of(e, raw, v, learned)
        val fp = item.fp()
        val twin = fp?.let { f -> near(f, item).firstOrNull { o -> o.fp()?.let { Fingerprint.same(it, o.at, f, item.at) } == true } }
        if (twin != null) {
            d.link(Link(itemId = twin.id, src = raw.source.name, sender = raw.sender, at = raw.at))
            val merged = twin.copy(
                last4 = twin.last4 ?: item.last4,
                bank = twin.bank ?: item.bank,
                merchant = twin.merchant ?: item.merchant,
            )
            if (merged != twin) d.update(merged)
            return Added(merged, false)
        }
        val id = d.add(item)
        d.link(Link(itemId = id, src = raw.source.name, sender = raw.sender, at = raw.at))
        fresh[id] = System.currentTimeMillis()
        return Added(item.copy(id = id), true)
    }

    private suspend fun near(f: Fingerprint, i: Item): List<Item> {
        if (f.group == Group.Code) return d.nearCode(i.code.orEmpty(), i.at - Fingerprint.WINDOW, i.at + Fingerprint.WINDOW)
        val span = if (f.group == Group.Due) 45L * 24 * 3600 * 1000 else Fingerprint.WINDOW
        return d.near(f.kinds(), f.paise, i.at - span, i.at + span)
    }
}
