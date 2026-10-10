package app.companion.link

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import app.companion.Services
import app.companion.core.MeetOpts
import app.companion.core.Query
import app.companion.data.Task
import app.companion.data.bill
import app.companion.data.money
import app.companion.system.Cals
import app.companion.system.Live
import app.companion.system.Plan
import app.companion.ui.dateOf
import app.companion.ui.has
import app.companion.ui.inr
import app.companion.ui.screens.spends
import app.companion.ui.zone
import java.time.LocalTime
import kotlinx.coroutines.flow.first

class LinkOff : Exception()

class LinkBad(why: String) : Exception(why)

class LinkCmds(private val c: Context, private val sl: Services, private val shares: () -> Set<String>) {
    private val repo get() = sl.repo

    suspend fun run(t: String, q: Obj): Obj = when (t) {
        "today" -> need("money") { today() }
        "ledger" -> need("money") { ledger(q) }
        "bills" -> need("money") { mapOf("open" to repo.billsNow().map(LinkShape::bill), "paid" to repo.paidNow(20).map(LinkShape::bill)) }
        "search" -> need("money") { mapOf("items" to repo.search(text(q, "q", 200)).first().filter { it.kind != "Otp" }.take(50).map(LinkShape::hit)) }
        "ask" -> need("ask") { mapOf("text" to ask(text(q, "q", 500))) }
        "pay" -> need("money") { item(q).takeIf { it.bill }?.let { repo.pay(it.id) } ?: throw LinkBad("gone") }
        "dismiss" -> need("money") { repo.dismiss(item(q).id) }
        "otp_drop" -> need("otp") { repo.dropOtp(id(q)).also { Live.refresh(c) } }
        "tasks" -> need("tasks") { mapOf("tasks" to tasks()) }
        "task_add" -> need("tasks") { mapOf("id" to add(q)) }
        "task_done" -> need("tasks") { Plan.finish(c, repo.task(id(q))?.takeUnless { it.gone }?.id ?: throw LinkBad("gone")) }
        "clip" -> need("clipIn") { clip(text(q, "text", CLIP)) }
        "ring" -> LinkRing.start(c)
        "ping" -> Unit
        else -> throw LinkBad("unknown")
    }.let { if (it is Map<*, *>) @Suppress("UNCHECKED_CAST") (it as Obj) else emptyMap() }

    private suspend fun <T> need(k: String, f: suspend () -> T): T {
        if (k !in shares()) throw LinkOff()
        return f()
    }

    private fun id(q: Obj) = (q["item"] as? Number)?.toLong() ?: throw LinkBad("item")

    private suspend fun item(q: Obj) = repo.item(id(q)) ?: throw LinkBad("gone")

    private fun text(q: Obj, k: String, max: Int) = (q[k] as? String)?.takeIf { it.isNotBlank() && it.length <= max } ?: throw LinkBad(k)

    private fun clip(text: String) {
        LinkEvents.echo = text
        c.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Mac", text))
    }

    private suspend fun today(): Obj {
        val now = System.currentTimeMillis()
        val on = shares()
        val day = dateOf(now)
        val rows = repo.money.first()
        val (spent, n) = repo.spentToday(now)
        val begin = day.minusDays(1).atStartOfDay(zone()).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone()).toInstant().toEpochMilli()
        val meets = if ("tasks" in on && c.has(Manifest.permission.READ_CALENDAR)) runCatching { Cals.meetings(c, now, end, MeetOpts()) }.getOrDefault(emptyList()) else emptyList()
        return mapOf(
            "greeting" to when (LocalTime.now(zone()).hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" },
            "spent" to mapOf("paise" to spent, "n" to n),
            "month" to mapOf("paise" to rows.filter { dateOf(it.at).let { d -> d.year == day.year && d.month == day.month } }.spends().sumOf { it.paise }, "budget" to (repo.profileNow().budget ?: 0L)),
            "otps" to if ("otp" in on) repo.otpsNow(now).map(LinkShape::otp) else emptyList<Obj>(),
            "needs" to if ("alerts" in on) repo.needing(10).map(LinkShape::alert) else emptyList<Obj>(),
            "bills" to repo.billsNow().map(LinkShape::bill),
            "tasks" to if ("tasks" in on) tasks() else emptyList<Obj>(),
            "meets" to meets.map(LinkShape::meet),
            "recent" to rows.filter { it.at in begin until end }.take(8).map(LinkShape::txn),
        )
    }

    private suspend fun ledger(q: Obj): Obj {
        val days = ((q["days"] as? Number)?.toInt() ?: 30).coerceIn(1, 92)
        val from = System.currentTimeMillis() - days * DAY
        val key = (q["q"] as? String)?.trim()?.lowercase().orEmpty()
        val rows = repo.money.first().filter { it.at >= from && it.money && (key.isEmpty() || listOfNotNull(it.title, it.merchant, it.category).any { s -> key in s.lowercase() }) }
        return mapOf("txns" to rows.take(300).map(LinkShape::txn))
    }

    private suspend fun tasks(): List<Obj> {
        val since = System.currentTimeMillis() - DAY
        return repo.allTasks().filter { !it.gone && (!it.done || it.upd >= since) }.sortedWith(compareBy({ it.done }, { it.remindAt ?: Long.MAX_VALUE })).take(100).map(LinkShape::task)
    }

    private suspend fun add(q: Obj): Long {
        val key = text(q, "key", 64)
        val ref = "mac:$key"
        repo.allTasks().firstOrNull { it.ref == ref }?.let { return it.id }
        val at = (q["remindAt"] as? Number)?.toLong()?.takeIf { it > 0 }
        return Plan.save(c, Task(title = text(q, "title", 200).trim(), note = (q["note"] as? String)?.take(1000)?.takeIf { it.isNotBlank() }, due = at?.let { dateOf(it).toEpochDay() }, remindAt = at, ref = ref)).id
    }

    private suspend fun ask(q: String): String {
        val plan = sl.planner.plan(q, System.currentTimeMillis()).takeIf { it.explicit }?.queries.orEmpty()
        if (plan.any { it is Query.CreateReminder || it is Query.CreateEvent }) throw LinkBad("confirm on phone")
        if (plan.isNotEmpty()) return plan.map { app.companion.ai.Answers.run(it, repo) }.joinToString("\n") { a -> a.says + (a.total?.let { " · ${inr(it)}" } ?: "") }
        val r = sl.chat.reply(q, bg = true)
        return r.text.takeIf { it.isNotBlank() } ?: r.ask?.takeIf { it.isNotBlank() } ?: throw LinkBad(r.failed ?: "no answer")
    }

    private companion object {
        const val DAY = 86_400_000L
        const val CLIP = 20_000
    }
}
