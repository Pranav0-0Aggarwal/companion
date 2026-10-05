package app.companion.chat

import app.companion.core.Card
import app.companion.core.DocKind
import app.companion.core.DocLine
import app.companion.core.Mask
import app.companion.core.Phrase
import app.companion.core.ToolSpecs
import app.companion.core.ToolOut
import app.companion.data.DocRow
import app.companion.data.Repo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val zone get() = ZoneId.systemDefault()

internal fun masked(r: DocRow) = "${r.kind} ${r.title}${r.mask?.let { " $it" }.orEmpty()}${r.expires?.let { " expires ${Instant.ofEpochMilli(it).atZone(zone).toLocalDate()}" }.orEmpty()}"

class VaultFind(private val repo: Repo, private val show: (Card) -> Unit) : Base(ToolSpecs.vaultFind) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val r = repo.docs.find(a.s("query") ?: return ToolOut.Fail("query")).take(8)
        val today = LocalDate.now(zone)
        if (r.isNotEmpty()) show(Card.Docs(r.map { DocLine(it.id, it.kind, it.title, it.mask, it.expires?.let { e -> ChronoUnit.DAYS.between(today, Instant.ofEpochMilli(e).atZone(zone).toLocalDate()).toInt() }) }))
        return ToolOut.Ok(if (r.isEmpty()) "No matching documents." else r.joinToString("; ") { masked(it) })
    }
}

class VaultAdd(private val repo: Repo) : Base(ToolSpecs.vaultAdd) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val kind = DocKind.entries.firstOrNull { it.name == a.s("kind") } ?: return ToolOut.Fail("kind")
        val exp = a.s("expires")?.let { e -> runCatching { LocalDate.parse(e) }.getOrNull() ?: Phrase(System.currentTimeMillis(), zone).slot(e)?.date }
        val number = a.s("number")
        if (number == null && exp == null) return ToolOut.Fail("number or expires")
        repo.docs.add(kind, a.s("title") ?: kind.label, number, exp, listOfNotNull(a.s("note")?.let { "note" to it }).toMap(), "manual")
        return ToolOut.Ok("Saved ${Mask.title(kind, number)}${exp?.let { " expiring $it" }.orEmpty()}.")
    }
}
