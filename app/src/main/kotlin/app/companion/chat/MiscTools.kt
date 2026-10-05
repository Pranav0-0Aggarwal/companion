package app.companion.chat

import android.Manifest
import android.content.Context
import app.companion.core.Phrase
import app.companion.core.Suggestion
import app.companion.core.ToolSpecs
import app.companion.core.ToolOut
import app.companion.data.Repo
import app.companion.system.Cals
import app.companion.system.Plan
import app.companion.ui.clock
import app.companion.ui.dateOf
import app.companion.ui.dayLabel
import app.companion.ui.has
import java.time.ZoneId

private val zone get() = ZoneId.systemDefault()

class Remind(private val c: Context) : Base(ToolSpecs.remind) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val now = System.currentTimeMillis()
        val at = Phrase(now, zone).slot(a.s("when") ?: return ToolOut.Fail("when"))?.millis(zone)?.takeIf { it > now } ?: return ToolOut.Fail("I could not read that time")
        Plan.remind(c, a.s("text") ?: return ToolOut.Fail("text"), at)
        return ToolOut.Ok("Reminder set for ${dayLabel(dateOf(at))} ${clock(at)}.")
    }
}

class CalendarAdd(private val c: Context, private val repo: Repo) : Base(ToolSpecs.calendarAdd) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val now = System.currentTimeMillis()
        val ph = Phrase(now, zone)
        val s = ph.slot(a.s("start") ?: return ToolOut.Fail("start"))?.millis(zone)?.takeIf { it > now } ?: return ToolOut.Fail("I could not read that time")
        val e = a.s("end")?.let { ph.slot(it)?.millis(zone) }?.takeIf { it > s } ?: (s + 3_600_000L)
        if (!c.has(Manifest.permission.WRITE_CALENDAR)) return ToolOut.Fail("Calendar access is not allowed.")
        val ok = Cals.add(c, repo.profileNow().cal, Suggestion.Cal(a.s("title") ?: return ToolOut.Fail("title"), s, e, false, ""))
        return if (ok) ToolOut.Ok("Added for ${dayLabel(dateOf(s))} ${clock(s)}.") else ToolOut.Fail("Could not add the event.")
    }
}

class Open(private val go: (String) -> Unit) : Base(ToolSpecs.open) {
    override suspend fun run(a: Map<String, Any?>): ToolOut {
        val s = a.s("screen")?.takeIf { it in ToolSpecs.screens } ?: return ToolOut.Fail("screen")
        go(s)
        return ToolOut.Ok("Opened $s.")
    }
}
