package app.companion.data

import androidx.room.withTransaction
import app.companion.core.Alerts
import app.companion.core.Body
import app.companion.core.Calibration
import app.companion.core.Tidy
import app.companion.core.CardFind
import app.companion.core.Category
import app.companion.core.Chats
import app.companion.core.Chan
import app.companion.core.Corrections
import app.companion.core.Cycles
import app.companion.core.Dup
import app.companion.core.Event
import app.companion.core.Filed
import app.companion.core.Fingerprint
import app.companion.core.Found
import app.companion.core.Flows
import app.companion.core.Labels
import app.companion.core.Paid
import app.companion.core.Phase
import app.companion.core.Merchant
import app.companion.core.Group
import app.companion.core.Kind
import app.companion.core.Repeat
import app.companion.core.Rules
import app.companion.core.Progress
import app.companion.core.Raw
import app.companion.core.Refile
import app.companion.core.Seen
import app.companion.core.Senders
import app.companion.core.Source
import app.companion.core.Template
import app.companion.core.Twin
import app.companion.core.Types
import app.companion.core.Verdict
import app.companion.core.Flow as Route
import app.companion.core.Worth
import app.companion.core.named
import app.companion.core.who
import app.companion.core.text
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class Added(val item: Item, val fresh: Boolean)

data class Change(val moved: Boolean, val ask: Boolean)

class Owed(val item: Item, val n: Int, val old: Boolean = false)

class Also(internal val prev: List<Pair<Long, Filed>>) {
    val n get() = prev.map { it.first }.distinct().size

    operator fun plus(o: Also) = Also(prev + o.prev)
}

class Renamed(internal val items: List<Item>, internal val before: List<Alias>, internal val key: String, internal val learned: Boolean, internal val moved: Boolean, internal val from: String, internal val to: String)

class Taught(val also: Also? = null, val sender: Pair<String, String>? = null, val note: String? = null)

class Repo(private val db: Db, c: android.content.Context) {
    private val d = db.dao()
    val life = Lives(db)
    val docs = Docs(c.applicationContext, db)
    val fresh = ConcurrentHashMap<Long, Long>()

    val profile: Flow<Profile> = d.profile().map { it ?: Profile() }
    val cards = d.cards()
    val money = d.money()
    val owed = d.bills().map { l -> Cycles.plan(l, Item::slip, now = System.currentTimeMillis()).filter { it.phase == Phase.Open || it.phase == Phase.Old }.map { Owed(it.head, it.items.size, it.phase == Phase.Old) } }
    val bills = owed.map { l -> l.filter { !it.old }.map(Owed::item) }
    val paid = d.paid().map { l -> Cycles.of(l, Item::slip).map { it.first() }.take(8) }
    val found = combine(d.cardRows().map { l -> CardFind.of(l.map { Seen(it.kind, it.bank, it.last4, it.at, it.due, it.text) }) }.flowOn(Dispatchers.Default), d.cards(), d.dismissed()) { f, cards, gone ->
        val have = cards.map { "${Cycles.issuer(it.bank)}:${it.last4}" }.toSet() + gone
        f.filter { it.key !in have }
    }
    val asks = d.asks()
    val tasks = d.tasks()
    val rules = d.ruleRows()
    val senderRules = d.senderRules()
    val learned = d.learnedRows()
    val moved = d.movedKeys().map { it.toSet() }
    val aliases = d.aliasRows()
    @Volatile private var own: Set<String>? = null

    suspend fun profileNow() = d.profileNow() ?: Profile()

    suspend fun save(p: Profile) = d.save(p)

    suspend fun edit(f: (Profile) -> Profile) = d.save(f(profileNow()))

    suspend fun addCard(c: Card) = d.addCard(c).also { own = null }

    suspend fun deleteCard(id: Long) = d.deleteCard(id).also { own = null }

    fun otps(now: Long = System.currentTimeMillis()) = d.otps(now)

    suspend fun dropOtp(id: Long) = d.expire(id, System.currentTimeMillis())

    suspend fun otpsNow(now: Long = System.currentTimeMillis()) = d.otpsNow(now)

    suspend fun billsNow() = cycles().map { it.first() }

    suspend fun cycles() = Cycles.plan(d.billsNow(), Item::slip, now = System.currentTimeMillis()).filter { it.phase == Phase.Open }.map { it.items }

    suspend fun addFound(f: Found) = f.takeIf { it.ready }?.let { addCard(Card(bank = it.bank, nick = "", last4 = it.last4, stmtDay = it.stmtDay!!, dueDay = it.dueDay!!, limit = it.limit?.takeIf { l -> l > 0 })) }

    suspend fun dismissFound(f: Found) = d.dismiss(Dismissed(f.key))

    suspend fun restoreFound(f: Found) = d.restore(f.key)

    suspend fun unpinged(chan: Chan, since: Long) = d.unpinged(Kind.entries.filter { Alerts.chan(it) == chan }.map { it.name }, since, Alerts.bit(chan))

    suspend fun addPing(id: Long, bit: Int) = d.addPing(id, bit)

    suspend fun deliveriesSince(since: Long) = d.deliveriesSince(since)

    suspend fun needs(since: Long) = d.needs(since)

    suspend fun needing(n: Int) = d.needing(n)

    suspend fun needCount() = d.needs(0)

    suspend fun merchants() = d.merchants()

    suspend fun restate(id: Long, state: String) = d.setState(id, state)

    suspend fun spentToday(now: Long = System.currentTimeMillis()): Pair<Long, Int> =
        d.spentSince(Alerts.dayStart(now, ZoneId.systemDefault())).filter { !it.cardPay && it.flow == null && it.currency == "INR" }.let { l -> l.sumOf { it.paise } to l.size }

    suspend fun item(id: Long) = d.item(id)

    fun suggested(now: Long = System.currentTimeMillis()) = d.suggested(now, now - 3 * 24 * 3600 * 1000L)

    suspend fun mirrored(now: Long = System.currentTimeMillis()) = d.mirrored(now)

    fun inbox(since: Long) = d.inbox(since)

    fun links(ids: List<Long>) = d.links(ids)

    fun tally(fromDay: Long) = d.tally(fromDay)

    fun read(from: Long, day: Long) = d.read(from, day)

    fun chats(since: Long) = d.chats(since)

    suspend fun important(name: String) = db.withTransaction {
        edit { it.copy(vip = Chats.vip(it.vip, name)) }
        d.promote(name)
    }

    fun search(q: String): Flow<List<Item>> {
        val t = q.split(Regex("\\s+")).map { w -> w.filter(Char::isLetterOrDigit) }.filter { it.isNotEmpty() }
        return if (t.isEmpty()) flowOf(emptyList()) else d.search(t.joinToString(" ") { "$it*" })
    }

    suspend fun sweep(now: Long = System.currentTimeMillis()) = d.sweep(now)

    suspend fun retain(now: Long = System.currentTimeMillis()) = d.blank(Body.since(profileNow().keep, now)) + d.purgeChats(now - Chats.KEEP)

    suspend fun keep(days: Int) {
        edit { it.copy(keep = days) }
        retain()
    }

    fun corrections(since: Long) = d.corrections(since)

    suspend fun exportRows() = d.exportRows()

    suspend fun deleteRule(hash: String, task: String) = d.deleteRule(hash, task)

    suspend fun deleteLearned(key: String) = d.deleteLearned(key)

    suspend fun deleteSender(key: String) = d.deleteSender(key)

    suspend fun deleteAlias(a: Alias) = db.withTransaction {
        d.deleteAlias(a.raw)
        val to = Merchant.clean(a.raw)
        if (to.isNotEmpty()) d.named().filter { it.merchant == a.name && Merchant.mentions(it.body, a.raw) }.forEach { r -> d.setNamed(r.id, to, r.title.takeIf { it.startsWith(a.name) }?.let { to + it.removePrefix(a.name) } ?: r.title) }
    }

    private suspend fun aliased(e: Event): Event = e.who()?.let { d.alias(Merchant.key(it)) }?.let { e.named(it) } ?: e

    private suspend fun name(from: String, to: String, auto: Boolean): Renamed? = db.withTransaction {
        val key = Merchant.key(from)
        val rows = d.named().filter { Merchant.key(it.merchant.orEmpty()) == key }
        val before = d.aliasesAround(key, from)
        d.repoint(from, to)
        d.putAliases(listOf(Alias(key, to, auto)))
        rows.forEach { r -> d.setNamed(r.id, to, r.merchant?.takeIf { r.title.startsWith(it) }?.let { to + r.title.removePrefix(it) } ?: r.title) }
        val (a, b) = Fingerprint.norm(from).orEmpty() to Fingerprint.norm(to).orEmpty()
        val l = a != b && d.learned(a) != null && d.learned(b) == null
        val m = a != b && d.isMoved(a) && !d.isMoved(b)
        if (l) d.rekeyLearned(a, b)
        if (m) d.rekeyMoved(a, b)
        Renamed(rows, before, key, l, m, a, b)
    }

    suspend fun rename(id: Long, to: String): Renamed? {
        val from = d.item(id)?.merchant ?: return null
        val n = to.trim().replace(Regex("\\s+"), " ").take(40)
        return if (n.isEmpty() || n == from) null else name(from, n, false)
    }

    suspend fun unname(r: Renamed) = db.withTransaction {
        r.items.forEach { d.setNamed(it.id, it.merchant, it.title) }
        d.dropAliases(r.before.map { it.raw } + r.key)
        d.putAliases(r.before)
        if (r.learned) d.rekeyLearned(r.to, r.from)
        if (r.moved) d.rekeyMoved(r.to, r.from)
    }

    private suspend fun learnName(i: Item, raw: Raw): Boolean {
        val m = i.merchant?.takeIf { i.money } ?: return false
        val to = Merchant.guess(m, raw.sender, raw.text()) ?: return false
        if (d.alias(Merchant.key(m)) != null) return false
        val seen = d.sendersOf(m).filter { Merchant.guess(m, it.sender) == to }.map { it.itemId }.distinct().size
        return Merchant.due(seen) && name(m, to, true) != null
    }

    suspend fun file(id: Long, category: String): Taught = db.withTransaction {
        val i = d.item(id) ?: return@withTransaction Taught()
        if (!i.money) return@withTransaction settle(i)
        val t = correct(i, Calibration.CATEGORY, category, src(i))
        d.file(id, category)
        i.merchant?.let { Fingerprint.norm(it) }?.let { d.learn(Learned(it, category)) }
        t
    }

    suspend fun confirm(id: Long): Taught = db.withTransaction { d.item(id)?.let { settle(it) } ?: Taught() }

    suspend fun spam(id: Long) = mark(id, "spam", null)

    suspend fun notSpam(id: Long) = mark(id, "alert", Src.NOT_SPAM)

    suspend fun notOtp(id: Long) = mark(id, "alert", Src.NOT_OTP)

    private suspend fun mark(id: Long, label: String, src: String?): Taught = db.withTransaction {
        val i = d.item(id) ?: return@withTransaction Taught()
        val t = correct(i, Calibration.TYPE, label, src ?: src(i))
        d.retype(id, if (label == "alert") Kind.Alert.name else Kind.Spam.name)
        t
    }

    suspend fun retype(id: Long, label: String, pick: String? = null): Taught = db.withTransaction {
        val i = d.item(id) ?: return@withTransaction Taught()
        val f = Labels.refile(label, i.filed(), i.paise, pick)
            ?: return@withTransaction Taught(note = if (Labels.needsAmount(label, Kind.valueOf(i.kind), i.paise)) "Needs an amount" else "Can't be filed as $label")
        d.refiled(id, f.kind, f.tags, f.category, f.state)
        val t = correct(i, Calibration.TYPE, label, src(i))
        val cat = f.category?.takeIf { pick != null && it == pick && it != i.category }
        if (cat == null) return@withTransaction t
        val moved = i.copy(kind = f.kind)
        val c = correct(moved, Calibration.CATEGORY, cat, Src.EDIT)
        i.merchant?.let { Fingerprint.norm(it) }?.let { d.learn(Learned(it, cat)) }
        Taught(listOfNotNull(t.also, c.also).reduceOrNull { a, b -> a + b }, t.sender ?: c.sender)
    }

    suspend fun tidy(now: Long): Also? = db.withTransaction {
        val moves = d.waiting().mapNotNull { i -> Tidy.of(i.filed(), i.money, i.merchant, i.credit, i.at, now)?.let { i.id to (i.filed() to it) } }
        moves.forEach { (id, p) -> d.refiled(id, p.second.kind, p.second.tags, p.second.category, p.second.state) }
        moves.takeIf { it.isNotEmpty() }?.let { m -> Also(m.map { it.first to it.second.first }) }
    }

    suspend fun settleAll(items: List<Item>): Also? = db.withTransaction {
        d.settleAll(items.map { it.id })
        items.takeIf { it.isNotEmpty() }?.let { l -> Also(l.map { it.id to it.filed() }) }
    }

    suspend fun undo(a: Also) = db.withTransaction {
        a.prev.asReversed().forEach { (id, f) -> d.refiled(id, f.kind, f.tags, f.category, f.state) }
    }

    suspend fun spending(id: Long, off: Boolean) = db.withTransaction {
        val key = d.item(id)?.merchant?.let { Fingerprint.norm(it) } ?: return@withTransaction
        if (off) d.putMoved(Moved(key)) else d.dropMoved(key)
        val own = own()
        d.merchantRows(0).filter { Fingerprint.norm(it.merchant) == key }.forEach { i ->
            val flow = when {
                off -> if (i.kind == Kind.Debit.name && i.flow == null) Route.Self.name else i.flow
                i.flow == Route.Self.name -> i.move()?.let { Flows.of(it, Corrections.text(i.title, i.note, i.body), own)?.name }
                else -> i.flow
            }
            if (flow != i.flow) d.setFlow(i.id, flow)
        }
    }

    private suspend fun settle(i: Item): Taught {
        val t = correct(i, Calibration.TYPE, Types.of(Kind.valueOf(i.kind)), src(i))
        d.setState(i.id, State.SETTLED)
        return t
    }

    private fun src(i: Item) = if (i.state == State.ASK) Src.ASK else Src.EDIT

    private suspend fun correct(i: Item, task: String, chosen: String, src: String): Taught {
        val typed = task == Calibration.TYPE
        d.correction(Correction(itemId = i.id, at = System.currentTimeMillis(), task = task, model = i.model.takeIf { typed }, modelProb = i.mprob.takeIf { typed }, chosen = chosen, src = src))
        i.tpl?.takeIf { typed || i.merchant == null }?.let { h ->
            val r = d.rule(h, task)
            d.putRule(TemplateRule(h, task, chosen, Rules.bump(r?.label, r?.count ?: 0, chosen)))
        }
        return Taught(similar(i, task, chosen), if (typed) sender(i, chosen) else null)
    }

    private suspend fun sender(i: Item, chosen: String): Pair<String, String>? {
        val key = d.senders(listOf(i.id)).firstOrNull()?.sender?.let(Senders::key)?.takeIf { it.isNotEmpty() } ?: return null
        if (!Senders.counts(chosen)) return null
        val r = d.senderRow(key)
        val n = Senders.bump(r?.label, r?.count ?: 0, chosen)
        d.putSender(SenderRule(key, chosen, n))
        return (key to chosen).takeIf { n == Senders.MIN }
    }

    private suspend fun similar(i: Item, task: String, chosen: String): Also? {
        val typed = task == Calibration.TYPE
        val key = i.merchant?.let { Fingerprint.norm(it) }
        val rows = when {
            !typed && key != null -> d.merchantRows(i.id).filter { Fingerprint.norm(it.merchant) == key }
            else -> i.tpl?.let { d.tplSiblings(it, i.id) }.orEmpty()
        }
        if (rows.isEmpty()) return null
        val corrected = d.corrected(rows.map { it.id }).toSet()
        val taught = d.taught(rows.mapNotNull { it.tpl }).toSet() - i.tpl
        val moves = rows.filter { !Refile.locked(it.filed(), it.merchant != null, it.id in corrected, it.tpl in taught) }.mapNotNull { r ->
            val old = r.filed()
            val new = when {
                !typed -> old.copy(category = chosen, state = State.SETTLED).takeIf { r.money && (r.credit || chosen != Category.Income.label) }
                Types.of(Kind.valueOf(r.kind)) == chosen -> old.copy(state = State.SETTLED)
                else -> Labels.refile(chosen, old, r.paise).takeUnless { r.money }
            }
            new?.takeIf { it != old }?.let { r.id to (old to it) }
        }
        if (moves.isEmpty()) return null
        moves.forEach { (id, p) -> d.refiled(id, p.second.kind, p.second.tags, p.second.category, p.second.state) }
        return Also(moves.map { it.first to it.second.first })
    }

    private suspend fun ruled(tpl: String, v: Verdict, raw: Raw): Pair<Verdict, String?> {
        val r = d.ruled(tpl).associate { it.task to it.label }
        val (t, cat) = Rules.apply(v, r, raw)
        return if (Calibration.TYPE in r) t to cat else Senders.apply(t, d.senderRule(Senders.key(raw.sender)), raw) to cat
    }

    private suspend fun own() = own ?: Flows.own(d.ownLast4() + d.cardLast4()).also { own = it }

    private suspend fun flowOf(e: Event, raw: Raw): String? {
        if (e !is Event.Move) return null
        if (e.merchant?.let { Fingerprint.norm(it) }?.let { d.isMoved(it) } == true) return Route.Self.name
        return Flows.of(e, raw.text(), own())?.name
    }

    suspend fun dismiss(id: Long) = d.setState(id, State.SETTLED)

    private suspend fun cycleOf(id: Long) = Cycles.of(d.billsNow(), Item::slip).firstOrNull { g -> g.any { it.id == id } }?.map(Item::id) ?: listOf(id)

    suspend fun pay(id: Long) = db.withTransaction {
        d.close(cycleOf(id), Paid.note(Paid.ME, System.currentTimeMillis()))
    }

    suspend fun keep(id: Long) = db.withTransaction {
        d.annotate(cycleOf(id), Paid.KEEP)
    }

    private suspend fun tidy(i: Item?) {
        if (i != null && (i.bill || i.cardBill)) db.withTransaction { d.settle(System.currentTimeMillis()) }
    }

    fun dups(id: Long) = d.dups(id)

    suspend fun dupCandidates(id: Long): List<Item> {
        val i = d.item(id) ?: return emptyList()
        return d.dupCandidates(Dup.kinds(i.kind), id, i.at, i.at - Dup.WINDOW, i.at + Dup.WINDOW, i.paise, Dup.LIMIT)
    }

    suspend fun markDup(id: Long, keep: Long): Boolean = db.withTransaction {
        val a = d.item(id) ?: return@withTransaction false
        val b = d.item(keep) ?: return@withTransaction false
        val to = b.dup ?: keep
        if (id == keep || to == id || !Dup.compatible(a.kind, b.kind)) return@withTransaction false
        d.moveDups(id, to)
        d.setDup(id, to)
        true
    }

    suspend fun unDup(id: Long) = d.setDup(id, null)

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

    suspend fun add(raw: Raw, verdict: Verdict, p: Profile, live: Boolean = false): Added? {
        val tpl = Template.of(raw)
        val (v, learnedCat) = ruled(tpl, verdict, raw)
        val e = aliased(v.event)
        val folded = foldKey(e, raw, v, p)
        if (folded != null) {
            d.count(day(raw), folded)
            if (live && !p.imported && raw.source == Source.Sms) d.fold(Fold(raw.sender, raw.at))
            return null
        }
        val learned = learnedCat ?: (e as? Event.Move)?.merchant?.let { Fingerprint.norm(it) }?.let { d.learned(it) }
        val item = Items.of(e, raw, v, learned, Body.since(p.keep, System.currentTimeMillis()), Chats.quiet(e, raw, p.vips)).copy(tpl = tpl, ping = if (live) 0 else Alerts.seen, model = v.guess?.label, mprob = v.guess?.prob, flow = flowOf(e, raw))
        val fp = item.fp()
        val twin = fp?.let { f ->
            val cands = near(f, item)
            val from = d.linksNow(cands.map { it.id }).groupBy { it.itemId }
            cands.filter { o ->
                val g = o.fp() ?: return@filter false
                Fingerprint.same(g, o.at, f, item.at) || from[o.id].orEmpty().any { l -> Fingerprint.echo(g, o.at, l.src, l.sender, f, item.at, raw.source.name, raw.sender) }
            }.minByOrNull { abs(it.at - item.at) }
        }
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
        if (item.flow == null && (item.kind == Kind.Debit.name || item.kind == Kind.Credit.name)) pairSelf(item.copy(id = id))
        d.link(Link(itemId = id, src = raw.source.name, sender = raw.sender, at = raw.at))
        if (e is Event.Move || e is Event.Statement) item.last4?.let { l -> own = own?.plus(l) }
        fresh[id] = System.currentTimeMillis()
        tidy(item)
        val kept = item.copy(id = id)
        return Added(if (learnName(kept, raw)) d.item(id) ?: kept else kept, true)
    }

    private fun day(raw: Raw) = Instant.ofEpochMilli(raw.at).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    private fun foldKey(e: Event, raw: Raw, v: Verdict, p: Profile) = when {
        e == Event.Promo -> "Promo"
        e == Event.Unknown && v is Verdict.Sure -> "Other"
        e == Event.Personal && !Chats.chat(raw.source) && !Worth.dm(raw.sender, raw.title + " " + raw.body, p.vips) -> raw.source.name
        else -> null
    }

    suspend fun refine(id: Long, raw: Raw, verdict: Verdict, p: Profile, was: String?): Change = db.withTransaction {
        val cur = d.item(id)?.takeIf { (was == null || it.state == was) && frozen(listOf(it)).isEmpty() } ?: return@withTransaction Change(false, false)
        val (v, cat) = ruled(cur.tpl ?: Template.of(raw), verdict, raw)
        val e = aliased(v.event)
        val key = foldKey(e, raw, v, p)
        if (key != null) {
            d.drop(id)
            d.moveDups(id, null)
            d.count(day(raw), key)
            if (!p.imported && raw.source == Source.Sms) d.fold(Fold(raw.sender, raw.at))
            fresh.remove(id)
            return@withTransaction Change(true, false)
        }
        val learned = cat ?: (e as? Event.Move)?.merchant?.let { Fingerprint.norm(it) }?.let { d.learned(it) }
        val n = Items.of(e, raw, v, learned, Body.since(p.keep, System.currentTimeMillis()), Chats.quiet(e, raw, p.vips)).copy(id = id, tpl = cur.tpl, ping = cur.ping, dup = cur.dup, model = v.guess?.label, mprob = v.guess?.prob, flow = flowOf(e, raw))
        if (n == cur || Refile.lost(cur.filed(), n.filed())) return@withTransaction Change(false, false)
        d.update(n)
        tidy(n)
        Change(true, Refile.asks(cur.filed(), n.filed()))
    }

    suspend fun <T> atomic(f: suspend () -> T): T = db.withTransaction { f() }

    suspend fun mark(job: String) = d.mark(job)

    suspend fun putMark(m: Mark) = d.putMark(m)

    suspend fun dropMark(job: String) = d.dropMark(job)

    suspend fun step(job: String, p: Progress) = d.step(job, p.pos, p.done, p.total, p.moved, p.ask, p.skip)

    suspend fun setPaused(job: String, paused: Boolean) = d.setPaused(job, paused)

    suspend fun top() = d.top()

    suspend fun topLink() = d.topLink()

    suspend fun unfold() = d.unfold()

    suspend fun seen(sender: String, sent: Long, date: Long, cap: Long): Boolean {
        val (a, b) = Twin.spans(sent, date)
        return d.seen(sender, a.first, a.last, b.first, b.last, cap)
    }

    suspend fun setWhy(job: String, why: String) = d.setWhy(job, why)

    suspend fun stale(after: Long, cap: Long) = d.stale(after, cap)

    suspend fun stale(after: Long, cap: Long, n: Int) = d.staleBatch(after, cap, n)

    suspend fun senders(ids: List<Long>) = d.senders(ids).distinctBy { it.itemId }.associate { it.itemId to it.sender }

    suspend fun frozen(items: List<Item>): Set<Long> {
        val corrected = d.corrected(items.map { it.id }).toSet()
        val taught = d.taught(items.mapNotNull { it.tpl }).toSet()
        return items.filter { Refile.locked(it.filed(), it.merchant != null, it.id in corrected, it.tpl in taught) }.map { it.id }.toSet()
    }

    suspend fun refile(id: Long, raw: Raw, v: Verdict, p: Profile): Change {
        val cur = d.item(id)?.takeIf { frozen(listOf(it)).isEmpty() } ?: return Change(false, false)
        val e = aliased(v.event)
        val learned = (e as? Event.Move)?.merchant?.let { Fingerprint.norm(it) }?.let { d.learned(it) }
        val n = Items.of(e, raw, v, learned, Body.since(p.keep, System.currentTimeMillis()), Chats.quiet(e, raw, p.vips))
        val old = cur.filed()
        val new = n.filed()
        val go = Refile.moved(old, new)
        val f = if (go) new else old
        d.refile(id, f.kind, f.tags, f.category, f.state, n.conf, v.guess?.label, v.guess?.prob)
        d.setFlow(id, flowOf(e, raw))
        if (go) tidy(d.item(id))
        return Change(go, Refile.asks(old, new))
    }

    private suspend fun pairSelf(i: Item) {
        val other = if (i.kind == Kind.Debit.name) Kind.Credit.name else Kind.Debit.name
        val o = d.near(listOf(other), i.paise, i.at - Fingerprint.WINDOW, i.at + Fingerprint.WINDOW).firstOrNull { it.dup == null && it.flow == null } ?: return
        d.setFlow(i.id, Route.Self.name)
        d.setFlow(o.id, Route.Self.name)
    }

    private suspend fun near(f: Fingerprint, i: Item): List<Item> {
        if (f.group == Group.Code) return d.nearCode(i.code.orEmpty(), i.at - Fingerprint.WINDOW, i.at + Fingerprint.WINDOW)
        val span = if (f.group == Group.Due) 45L * 24 * 3600 * 1000 else Fingerprint.ECHO
        return d.near(f.kinds(), f.paise, i.at - span, i.at + span)
    }
}
