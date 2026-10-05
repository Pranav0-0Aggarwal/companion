package app.companion.chat

import app.companion.core.Phrase
import app.companion.core.ToolSpecs
import app.companion.core.ToolOut
import app.companion.data.Repo
import app.companion.data.TripRow
import app.companion.ui.inr
import java.time.LocalDate
import java.time.ZoneId

private val zone get() = ZoneId.systemDefault()

private fun day(d: LocalDate, end: Boolean = false) = (if (end) d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1 else d.atStartOfDay(zone).toInstant().toEpochMilli())

internal suspend fun describe(repo: Repo, t: TripRow): String {
    val s = repo.life.summary(t)
    val top = s.top.joinToString(", ") { "${it.first} ${inr(it.second)}" }
    return "${t.name}: ${inr(s.total)} over ${s.days} days, ${inr(s.perDay)} a day${if (top.isNotEmpty()) ". Top: $top" else ""}."
}

class StartTrip(private val repo: Repo) : Base(ToolSpecs.startTrip) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val now = System.currentTimeMillis()
        val ph = Phrase(now, zone)
        val from = a.s("from")?.let { ph.slot(it)?.date } ?: LocalDate.now(zone)
        val to = a.s("to")?.let { ph.slot(it)?.date }?.takeIf { !it.isBefore(from) } ?: from.plusDays(14)
        val id = repo.life.startTrip(a.s("name") ?: return ToolOut.Fail("name"), day(from), day(to, true))
        return ToolOut.Ok("Trip started from $from to $to. ${describe(repo, repo.life.trips().first { it.id == id })}")
    }
}

class EndTrip(private val repo: Repo) : Base(ToolSpecs.endTrip) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val t = repo.life.endTrip(System.currentTimeMillis()) ?: return ToolOut.Fail("There is no active trip.")
        return ToolOut.Ok("Ended. ${describe(repo, t)}")
    }
}

class TripSummaryTool(private val repo: Repo) : Base(ToolSpecs.tripSummary) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val t = a.s("name")?.let { repo.life.tripNamed(it) } ?: repo.life.activeTrip() ?: repo.life.trips().firstOrNull() ?: return ToolOut.Fail("No trips yet.")
        return ToolOut.Ok(describe(repo, t))
    }
}
