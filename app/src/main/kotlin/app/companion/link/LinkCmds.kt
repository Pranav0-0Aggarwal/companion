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
import app.companion.link.bulk.Bulk
import app.companion.link.powers.Powers
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

class LinkCmds(private val c: Context, private val sl: Services, private val shares: () -> Set<String>, private val okm: () -> ByteArray?, private val powers: Powers) {
    private val repo get() = sl.repo

    suspend fun run(t: String, q: Obj): Obj = when (t) {
        "today" -> need("money") { today() }
        "ledger" -> need("money") { ledger(q) }
        "bills" -> need("money") { mapOf("open" to repo.billsNow().map(LinkShape::bill), "paid" to repo.paidNow(20).map(LinkShape::bill)) }
        "search" -> need("money") { mapOf("items" to repo.search(LinkArgs.text(q, "q", 200)).first().filter { it.kind != "Otp" }.take(50).map(LinkShape::hit)) }
        "ask" -> need("ask") { mapOf("text" to ask(LinkArgs.text(q, "q", 500))) }
        "pay" -> need("money") { item(q).takeIf { it.bill }?.let { repo.pay(it.id) } ?: throw LinkBad("gone") }
        "dismiss" -> need("money") { repo.dismiss(item(q).id) }
        "otp_drop" -> need("otp") { repo.dropOtp(LinkArgs.item(q)).also { Live.refresh(c) } }
        "tasks" -> need("tasks") { mapOf("tasks" to tasks()) }
        "task_add" -> need("tasks") { mapOf("id" to add(q)) }
        "task_done" -> need("tasks") { Plan.finish(c, repo.task(LinkArgs.item(q))?.takeUnless { it.gone }?.id ?: throw LinkBad("gone")) }
        "clip" -> need("clipIn") { clip(LinkArgs.text(q, "text", CLIP)) }
        "bulk" -> need(Bulk.share(q)) { Bulk.open(okm() ?: throw LinkOff(), q) }
        "ring" -> LinkRing.start(c)
        "bridge_started" -> powers.started()
        "ping" -> Unit
        else -> LinkFeatures.of(t)?.let { f -> need(f.key) { f.run(t, q) } } ?: throw LinkBad("unknown")
    }.let { if (it is Map<*, *>) @Suppress("UNCHECKED_CAST") (it as Obj) else emptyMap() }

    private suspend fun <T> need(k: String, f: suspend () -> T): T {
        if (k !in shares()) throw LinkOff()
        return f()
    }

    private suspend fun item(q: Obj) = repo.item(LinkArgs.item(q)) ?: throw LinkBad("gone")

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
        return LinkShape.today(
            when (LocalTime.now(zone()).hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" },
            spent, n,
            rows.filter { dateOf(it.at).let { d -> d.year == day.year && d.month == day.month } }.spends().sumOf { it.paise }, repo.profileNow().budget ?: 0L,
            if ("otp" in on) repo.otpsNow(now).map(LinkShape::otp) else emptyList(),
            if ("alerts" in on) repo.needing(10).map(LinkShape::alert) else emptyList(),
            repo.billsNow().map(LinkShape::bill),
            if ("tasks" in on) tasks() else emptyList(),
            meets.map(LinkShape::meet),
            rows.filter { it.at in begin until end }.take(8).map(LinkShape::txn),
        )
    }

    private suspend fun ledger(q: Obj): Obj {
        val days = LinkArgs.days(q)
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
        val key = LinkArgs.text(q, "key", 64)
        val ref = "mac:$key"
        repo.allTasks().firstOrNull { it.ref == ref }?.let { return it.id }
        val at = LinkArgs.at(q)
        return Plan.save(c, Task(title = LinkArgs.text(q, "title", 200).trim(), note = (q["note"] as? String)?.take(1000)?.takeIf { it.isNotBlank() }, due = at?.let { dateOf(it).toEpochDay() }, remindAt = at, ref = ref)).id
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
