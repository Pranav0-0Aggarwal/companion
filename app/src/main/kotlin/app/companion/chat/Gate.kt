package app.companion.chat

import app.companion.core.Cycles
import app.companion.core.Do
import app.companion.core.Held
import app.companion.core.Pending
import app.companion.core.Senders
import app.companion.data.Repo
import app.companion.data.State
import app.companion.data.bill
import app.companion.data.money
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class Undo(val text: String, val run: suspend () -> Unit)

class Done(val ok: Boolean, val said: String, val undos: List<Undo> = emptyList())

class Gate(private val repo: Repo) {
    private val held = Held()

    fun hold(act: Do, text: String): Pair<Pending, Boolean> = held.add(act, text)

    fun cancel(key: Long) = held.cancel(key) != null

    fun clear() = held.clear()

    suspend fun confirm(key: Long): Done? {
        val p = held.confirm(key) ?: return null
        val d = withContext(Dispatchers.IO) {
            try {
                exec(p.act)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                bad("That did not work. Nothing was changed.")
            }
        }
        held.finish(key, d.ok)
        return d
    }

    private fun bad(why: String) = Done(false, why)

    private suspend fun exec(a: Do): Done = when (a) {
        is Do.Pay -> {
            val i = repo.item(a.id)?.takeIf { it.bill && it.state != State.PAID } ?: return bad("That bill is gone or already paid.")
            repo.pay(i.id)
            Done(true, "${i.title} marked as paid.")
        }
        is Do.File -> {
            val i = repo.item(a.id)?.takeIf { it.money } ?: return bad("That payment is gone.")
            val t = repo.file(i.id, a.category)
            Done(true, "Filed ${i.title} under ${a.category}.", listOfNotNull(t.also?.let { x -> Undo("Also filed ${x.n} similar") { repo.undo(x) } }))
        }
        is Do.Retype -> {
            val i = repo.item(a.id) ?: return bad("That item is gone.")
            val t = repo.retype(i.id, a.label)
            t.note?.let { return bad(it) }
            Done(
                true, "${i.title} is now ${a.label}.",
                listOfNotNull(
                    t.also?.let { x -> Undo("Also filed ${x.n} similar") { repo.undo(x) } },
                    t.sender?.let { (k, l) -> Undo("Always treating ${Senders.name(k)} as $l") { repo.deleteSender(k) } },
                ),
            )
        }
        is Do.Dup -> {
            if (!repo.markDup(a.id, a.keep)) return bad("Could not mark that as a duplicate.")
            Done(true, "Marked as a duplicate.", listOf(Undo("Marked as duplicate") { repo.unDup(a.id) }))
        }
        is Do.Rename -> {
            val r = repo.rename(a.id, a.to) ?: return bad("Could not rename ${a.from}.")
            Done(true, "Renamed ${a.from} to ${a.to}.", listOf(Undo("Renamed ${a.from} to ${a.to}") { repo.unname(r) }))
        }
        is Do.Moved -> {
            repo.item(a.id) ?: return bad("That merchant is gone.")
            repo.spending(a.id, true)
            Done(true, "${a.merchant} no longer counts as spending.", listOf(Undo("${a.merchant} counts as spending again") { repo.spending(a.id, false) }))
        }
        is Do.AddCard -> {
            val f = repo.found.first().firstOrNull { it.last4 == a.last4 && Cycles.issuer(it.bank) == Cycles.issuer(a.bank) }?.takeIf { it.ready } ?: return bad("That card is not available to add.")
            repo.addFound(f)
            Done(true, "Added ${f.bank} ··${f.last4}.", listOf(Undo("Added ${f.bank} ··${f.last4}") { repo.cards.first().lastOrNull { it.last4 == f.last4 && Cycles.issuer(it.bank) == Cycles.issuer(f.bank) }?.let { repo.deleteCard(it.id) } }))
        }
        is Do.Budget -> {
            val was = repo.profileNow().budget
            repo.edit { it.copy(budget = a.paise) }
            Done(true, "Monthly budget set.", listOf(Undo("Budget changed") { repo.edit { it.copy(budget = was) } }))
        }
        is Do.Dismiss -> {
            val i = repo.item(a.id)?.takeIf { it.state == State.ASK || it.state == State.CHECK } ?: return bad("That item is already handled.")
            repo.dismiss(i.id)
            Done(true, "Dismissed ${i.title}.", listOf(Undo("Dismissed ${i.title}") { repo.restate(i.id, i.state) }))
        }
    }
}
