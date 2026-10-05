package app.companion.chat

import app.companion.ai.Answers
import app.companion.core.Phrase
import app.companion.core.Query
import app.companion.core.ToolSpecs
import app.companion.core.ToolOut
import app.companion.data.Repo
import app.companion.ui.inr
import java.time.ZoneId
import kotlin.math.abs
import kotlinx.coroutines.flow.first

private val zone get() = ZoneId.systemDefault()

class Spend(private val repo: Repo) : Base(ToolSpecs.spend) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val ph = Phrase(System.currentTimeMillis(), zone)
        val (x, y) = ph.period(a.s("period") ?: return ToolOut.Fail("period")) ?: return ToolOut.Fail("I could not read that period")
        val one = Answers.run(Query.SumSpend(a.s("category"), a.s("merchant"), null, x, y), repo)
        var out = "${one.says}: ${inr(one.total ?: 0)} in ${one.count} payments."
        val other = a.s("compare")?.let { ph.period(it) }
        if (other != null) {
            val two = Answers.run(Query.SumSpend(a.s("category"), a.s("merchant"), null, other.first, other.second), repo)
            val diff = (one.total ?: 0) - (two.total ?: 0)
            out += " ${two.says}: ${inr(two.total ?: 0)}. ${if (diff >= 0) "More" else "Less"} by ${inr(abs(diff))}."
        }
        return ToolOut.Ok(out)
    }
}

class Bills(private val repo: Repo) : Base(ToolSpecs.bills) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val (x, y) = Phrase(System.currentTimeMillis(), zone).period(a.s("range") ?: return ToolOut.Fail("range"), true) ?: return ToolOut.Fail("I could not read that range")
        val r = Answers.run(Query.ListBills(x, y, a.b("unpaid_only") ?: false), repo)
        if (r.lines.isEmpty()) return ToolOut.Ok("${r.says}: none.")
        return ToolOut.Ok("${r.says}: ${inr(r.total ?: 0)} across ${r.count}. " + r.lines.joinToString("; ") { "${it.title} ${inr(it.paise)} ${it.sub}" })
    }
}

class Cards(private val repo: Repo) : Base(ToolSpecs.cards) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val cs = repo.cards.first()
        if (cs.isEmpty()) return ToolOut.Ok("No cards added yet.")
        return ToolOut.Ok(cs.joinToString("; ") { "${it.bank} ${it.nick} ··${it.last4}, statement day ${it.stmtDay}, due day ${it.dueDay}${it.limit?.let { l -> ", limit ${inr(l)}" }.orEmpty()}" })
    }
}

class Balance : Base(ToolSpecs.balance) {
    override suspend fun run(a: Map<String, Any?>) = ToolOut.Ok("Account balances are not tracked yet.")
}
