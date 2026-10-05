package app.companion.chat

import app.companion.core.Card
import app.companion.core.Category
import app.companion.core.Cycles
import app.companion.core.Do
import app.companion.core.Dues
import app.companion.core.Dup
import app.companion.core.Labels
import app.companion.core.Names
import app.companion.core.Ref
import app.companion.core.Rs
import app.companion.core.ToolOut
import app.companion.core.ToolSpecs
import app.companion.core.Types
import app.companion.core.Kind
import app.companion.data.Item
import app.companion.data.Mer
import app.companion.data.Repo
import app.companion.data.State
import app.companion.data.bill
import app.companion.data.credit
import app.companion.data.filed
import app.companion.data.money
import kotlinx.coroutines.flow.first

private fun Gate.offer(act: Do, text: String, show: (Card) -> Unit): ToolOut {
    val (p, fresh) = hold(act, text)
    if (fresh) show(Card.Confirm(p.key, text))
    return ToolOut.Ok(if (fresh) "Not done yet. Waiting for the user to confirm: $text" else "Still waiting for the user to confirm: $text")
}

private suspend fun Repo.target(ref: String?): Item? = Ref.id(ref)?.let { item(it) }

private fun miss(ref: String?) = ToolOut.Fail(if (Ref.id(ref) == null) "use an id like i:12 from a lookup" else "no such item")

private fun amount(i: Item) = if (i.paise > 0) " ${Rs.of(i.paise)}" else ""

class MarkPaid(private val repo: Repo, private val gate: Gate, private val show: (Card) -> Unit) : Base(ToolSpecs.markPaid) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val q = a.s("bill") ?: return ToolOut.Fail("bill")
        repo.target(q)?.takeIf { it.bill && it.state != State.PAID }?.let { return pay(it) }
        val (i, out) = repo.byName(q)
        return i?.let(::pay) ?: out!!
    }

    private fun pay(i: Item) = gate.offer(Do.Pay(i.id), "Mark ${i.title}${amount(i)} as paid?", show)
}

private suspend fun Repo.byName(q: String): Pair<Item?, ToolOut?> {
    val open = cycles().map { it.first() }
    val rows = Dues.pick(open.map { it.dueRow(false) }, q)
    return when (rows.size) {
        0 -> null to ToolOut.Fail("no open bill matches")
        1 -> open.first { it.id == rows[0].id } to null
        else -> null to ToolOut.Ask("Which bill? " + rows.take(4).joinToString(" or ") { "${it.title} ${Rs.of(it.paise)}" })
    }
}

class FileSpend(private val repo: Repo, private val gate: Gate, private val show: (Card) -> Unit) : Base(ToolSpecs.file) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val i = repo.target(a.s("item")) ?: return miss(a.s("item"))
        val c = Category.entries.firstOrNull { it.label == a.s("category") } ?: return ToolOut.Fail("category")
        if (!i.money) return ToolOut.Fail("only payments can be filed")
        if (c == Category.Income && !i.credit) return ToolOut.Fail("a payment out cannot be income")
        if (i.category == c.label) return ToolOut.Ok("Already filed under ${c.label}.")
        return gate.offer(Do.File(i.id, c.label), "File ${i.title}${amount(i)} under ${c.label}?", show)
    }
}

class Retype(private val repo: Repo, private val gate: Gate, private val show: (Card) -> Unit) : Base(ToolSpecs.retype) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val i = repo.target(a.s("item")) ?: return miss(a.s("item"))
        val l = a.s("label")?.takeIf { it in Labels.pick } ?: return ToolOut.Fail("label")
        if (Types.of(Kind.valueOf(i.kind)) == l) return ToolOut.Ok("Already $l.")
        if (Labels.refile(l, i.filed(), i.paise) == null) return ToolOut.Fail(if (Labels.needsAmount(l, Kind.valueOf(i.kind), i.paise)) "needs an amount" else "cannot be $l")
        return gate.offer(Do.Retype(i.id, l), "Change ${i.title}${amount(i)} to $l?", show)
    }
}

class MarkDup(private val repo: Repo, private val gate: Gate, private val show: (Card) -> Unit) : Base(ToolSpecs.markDup) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val i = repo.target(a.s("item")) ?: return miss(a.s("item"))
        val k = repo.target(a.s("keep")) ?: return miss(a.s("keep"))
        if (i.id == k.id || !Dup.compatible(i.kind, k.kind)) return ToolOut.Fail("those cannot be duplicates")
        return gate.offer(Do.Dup(i.id, k.id), "Mark ${i.title}${amount(i)} as a duplicate of ${k.title}${amount(k)}?", show)
    }
}

private suspend fun Repo.merchant(q: String?): Pair<Mer?, ToolOut?> {
    val all = merchants()
    val hit = Names.find(q ?: return null to ToolOut.Fail("merchant"), all.map { it.merchant })
    return when (hit.size) {
        0 -> null to ToolOut.Fail("no such merchant")
        1 -> all.first { it.merchant == hit[0] } to null
        else -> null to ToolOut.Ask("Which merchant? " + hit.take(4).joinToString(" or "))
    }
}

class RenameMerchant(private val repo: Repo, private val gate: Gate, private val show: (Card) -> Unit) : Base(ToolSpecs.renameMerchant) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val (m, out) = repo.merchant(a.s("from"))
        m ?: return out!!
        val to = a.s("to")?.replace(Regex("\\s+"), " ")?.take(40)?.trim()?.takeIf { it.isNotEmpty() && it != m.merchant } ?: return ToolOut.Fail("to")
        return gate.offer(Do.Rename(m.id, m.merchant, to), "Rename ${m.merchant} to $to everywhere?", show)
    }
}

class NotSpending(private val repo: Repo, private val gate: Gate, private val show: (Card) -> Unit) : Base(ToolSpecs.notSpending) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val (m, out) = repo.merchant(a.s("merchant"))
        m ?: return out!!
        return gate.offer(Do.Moved(m.id, m.merchant), "Stop counting ${m.merchant} as spending?", show)
    }
}

class AddCard(private val repo: Repo, private val gate: Gate, private val show: (Card) -> Unit) : Base(ToolSpecs.addCard) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val bank = a.s("bank") ?: return ToolOut.Fail("bank")
        val last4 = a.s("last4")?.takeIf { Regex("\\d{4}").matches(it) } ?: return ToolOut.Fail("last4")
        val f = repo.found.first().firstOrNull { it.last4 == last4 && Cycles.issuer(it.bank) == Cycles.issuer(bank) } ?: return ToolOut.Fail("no such card in your messages")
        if (!f.ready) return ToolOut.Fail("needs statement and due days, add it in Cards")
        return gate.offer(Do.AddCard(f.bank, f.last4), "Add ${f.bank} ··${f.last4} to your cards?", show)
    }
}

class SetBudget(private val gate: Gate, private val show: (Card) -> Unit) : Base(ToolSpecs.setBudget) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val rupees = a.d("amount")?.let { Math.round(it) }?.takeIf { it in 1..MAX } ?: return ToolOut.Fail("amount")
        return gate.offer(Do.Budget(rupees * 100), "Set your monthly budget to ${Rs.of(rupees * 100)}?", show)
    }

    private companion object {
        const val MAX = 999_999_999L
    }
}

class Dismiss(private val repo: Repo, private val gate: Gate, private val show: (Card) -> Unit) : Base(ToolSpecs.dismiss) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val i = repo.target(a.s("item")) ?: return miss(a.s("item"))
        if (i.state != State.ASK && i.state != State.CHECK) return ToolOut.Fail("that item is not waiting on you")
        return gate.offer(Do.Dismiss(i.id), "Dismiss ${i.title}${amount(i)}?", show)
    }
}
