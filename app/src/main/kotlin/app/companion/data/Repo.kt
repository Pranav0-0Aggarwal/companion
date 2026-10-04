package app.companion.data

import androidx.room.withTransaction
import app.companion.core.Body
import app.companion.core.Calibration
import app.companion.core.Event
import app.companion.core.Fingerprint
import app.companion.core.Group
import app.companion.core.Kind
import app.companion.core.Repeat
import app.companion.core.Rules
import app.companion.core.Raw
import app.companion.core.Template
import app.companion.core.Types
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
    val rules = d.ruleRows()

    suspend fun profileNow() = d.profileNow() ?: Profile()

    suspend fun save(p: Profile) = d.save(p)

    suspend fun edit(f: (Profile) -> Profile) = d.save(f(profileNow()))

    suspend fun addCard(c: Card) = d.addCard(c)

    suspend fun deleteCard(id: Long) = d.deleteCard(id)

    fun otps(now: Long = System.currentTimeMillis()) = d.otps(now)

    suspend fun otpsNow(now: Long = System.currentTimeMillis()) = d.otpsNow(now)

    suspend fun billsNow() = d.billsNow()

    suspend fun item(id: Long) = d.item(id)

    fun suggested(now: Long = System.currentTimeMillis()) = d.suggested(now, now - 3 * 24 * 3600 * 1000L)

    fun inbox(since: Long) = d.inbox(since)

    fun links(ids: List<Long>) = d.links(ids)

    fun tally(fromDay: Long) = d.tally(fromDay)

    fun search(q: String): Flow<List<Item>> {
        val t = q.split(Regex("\\s+")).map { w -> w.filter(Char::isLetterOrDigit) }.filter { it.isNotEmpty() }
        return if (t.isEmpty()) flowOf(emptyList()) else d.search(t.joinToString(" ") { "$it*" })
    }

    suspend fun sweep(now: Long = System.currentTimeMillis()) = d.sweep(now)

    suspend fun retain(now: Long = System.currentTimeMillis()) = d.blank(Body.since(profileNow().keep, now))

    suspend fun keep(days: Int) {
        edit { it.copy(keep = days) }
        retain()
    }

    fun corrections(since: Long) = d.corrections(since)

    suspend fun exportRows() = d.exportRows()

    suspend fun deleteRule(hash: String, task: String) = d.deleteRule(hash, task)

    suspend fun file(id: Long, category: String) = db.withTransaction {
        val i = d.item(id) ?: return@withTransaction
        if (!i.money) return@withTransaction settle(i)
        correct(i, Calibration.CATEGORY, category, src(i))
        d.file(id, category)
        i.merchant?.let { Fingerprint.norm(it) }?.let { d.learn(Learned(it, category)) }
    }

    suspend fun confirm(id: Long) = db.withTransaction { d.item(id)?.let { settle(it) } }

    suspend fun spam(id: Long) = mark(id, "spam", null)

    suspend fun notSpam(id: Long) = mark(id, "alert", Src.NOT_SPAM)

    suspend fun notOtp(id: Long) = mark(id, "alert", Src.NOT_OTP)

    private suspend fun mark(id: Long, label: String, src: String?) = db.withTransaction {
        val i = d.item(id) ?: return@withTransaction
        correct(i, Calibration.TYPE, label, src ?: src(i))
        d.retype(id, if (label == "alert") Kind.Alert.name else Kind.Spam.name)
    }

    private suspend fun settle(i: Item) {
        correct(i, Calibration.TYPE, Types.of(Kind.valueOf(i.kind)), src(i))
        d.setState(i.id, State.SETTLED)
    }

    private fun src(i: Item) = if (i.state == State.ASK) Src.ASK else Src.EDIT

    private suspend fun correct(i: Item, task: String, chosen: String, src: String) {
        val typed = task == Calibration.TYPE
        d.correction(Correction(itemId = i.id, at = System.currentTimeMillis(), task = task, model = i.model.takeIf { typed }, modelProb = i.mprob.takeIf { typed }, chosen = chosen, src = src))
        val h = i.tpl ?: return
        val r = d.rule(h, task)
        d.putRule(TemplateRule(h, task, chosen, Rules.bump(r?.label, r?.count ?: 0, chosen)))
    }

    private suspend fun ruled(tpl: String, v: Verdict) = Rules.apply(v, d.ruled(tpl).associate { it.task to it.label })

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

    suspend fun add(raw: Raw, verdict: Verdict, p: Profile): Added? {
        val tpl = Template.of(raw)
        val (v, learnedCat) = ruled(tpl, verdict)
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
        val learned = learnedCat ?: (e as? Event.Move)?.merchant?.let { Fingerprint.norm(it) }?.let { d.learned(it) }
        val item = Items.of(e, raw, v, learned, Body.since(p.keep, System.currentTimeMillis())).copy(tpl = tpl, model = v.guess?.label, mprob = v.guess?.prob)
        val fp = item.fp()
        val twin = fp?.let { f -> near(f, item).firstOrNull { o -> o.fp()?.let { Fingerprint.same(it, o.at, f, item.at) } == true } }
        if (twin != null) {
            d.link(Link(itemId = twin.id, src = raw.source.name, sender = raw.sender, at = raw.at))
            val merged = twin.copy(
                last4 = twin.last4 ?: item.last4,
                bank = twin.bank ?: item.bank,
                merchant = twin.merchant ?: item.merchant,
                body = twin.body ?: item.body,
                tags = twin.tags ?: item.tags,
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
