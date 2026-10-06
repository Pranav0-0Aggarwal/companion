package app.companion.data

import androidx.room.withTransaction
import app.companion.core.Dupes
import app.companion.core.Fingerprint
import app.companion.core.Flow
import app.companion.core.Group
import app.companion.core.Kind
import app.companion.core.Leg
import app.companion.core.Merchant
import app.companion.core.Raw
import app.companion.core.Sig
import app.companion.core.Transfers
import app.companion.core.text
import kotlin.math.abs

class Reconcile(private val db: Db) {
    private val d = db.dao()

    suspend fun twin(item: Item, raw: Raw): Item? {
        val f = item.fp() ?: return null
        val me = Sig(f, item.at, raw.source.name, raw.sender, Dupes.ref(raw.text()))
        val cands = near(f, item)
        val from = d.linksNow(cands.map { it.id }).groupBy { it.itemId }
        return cands.filter { o -> sigs(o, from[o.id].orEmpty()).any { Dupes.same(it, me) } }.minByOrNull { abs(it.at - item.at) }
    }

    suspend fun pair(i: Item) {
        if (i.flow != null || !i.leg) return
        val near = d.near(listOf(Kind.Debit.name, Kind.Credit.name), i.paise, i.at - Fingerprint.WINDOW, i.at + Fingerprint.WINDOW)
            .filter { it.dup == null && it.flow == null && it.leg }
        selves(Transfers.pairs((near + i).distinctBy { it.id }.map { it.legOf() }).filter { (o, n) -> i.id == o.id || i.id == n.id }, near + i)
    }

    suspend fun sweep(since: Long) = db.withTransaction {
        val rows = d.movesSince(since)
        val from = d.linksNow(rows.map { it.id }).groupBy { it.itemId }
        val dups = Dupes.sweep(rows.map { it.id to sigs(it, from[it.id].orEmpty()) })
        dups.forEach { (id, keep) ->
            d.moveDups(id, keep)
            d.setDup(id, keep)
        }
        val live = rows.filter { it.id !in dups && it.leg && (it.flow == null || it.flow == Flow.Self.name) }
        selves(Transfers.pairs(live.map { it.legOf() }), live)
        val own = d.movedNow().toSet()
        live.filter { it.flow == null && !it.credit && key(it) in own }.forEach { d.setFlow(it.id, Flow.Self.name) }
    }

    private suspend fun selves(pairs: List<Pair<Leg, Leg>>, items: List<Item>) {
        val byId = items.associateBy { it.id }
        pairs.forEach { (o, n) ->
            d.setFlow(o.id, Flow.Self.name)
            d.setFlow(n.id, Flow.Self.name)
            byId[o.id]?.takeIf { it.merchant?.let(Merchant::brand) == null }?.let(::key)?.let { d.putMoved(Moved(it)) }
        }
    }

    private suspend fun near(f: Fingerprint, i: Item): List<Item> {
        if (f.group == Group.Code) return d.nearCode(i.code.orEmpty(), i.at - Fingerprint.WINDOW, i.at + Fingerprint.WINDOW)
        val span = if (f.group == Group.Due) DUE else Dupes.SPAN
        return d.near(f.kinds(), f.paise, i.at - span, i.at + span)
    }

    private fun sigs(i: Item, links: List<Link>): List<Sig> {
        val f = i.fp() ?: return emptyList()
        val ref = i.ref()
        return links.map { Sig(f, i.at, it.src, it.sender, ref) }.ifEmpty { listOf(Sig(f, i.at, i.src, null, ref)) }
    }

    private fun key(i: Item) = i.merchant?.let { Fingerprint.norm(it) }

    private val Item.leg get() = kind == Kind.Debit.name || kind == Kind.Credit.name

    private fun Item.legOf() = Leg(id, kind == Kind.Debit.name, paise, at)

    private companion object {
        const val DUE = 45L * 24 * 3600 * 1000
    }
}
