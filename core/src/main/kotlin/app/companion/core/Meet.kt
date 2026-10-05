package app.companion.core

import kotlin.math.ceil

enum class Via(val label: String) { Meet("Meet"), Zoom("Zoom"), Teams("Teams"), Webex("Webex") }

data class Join(val via: Via, val url: String)

data class Meeting(val id: Long, val title: String, val start: Long, val end: Long, val place: String?, val join: Join?, val cal: Long)

data class MeetOpts(
    val on: Boolean = false,
    val cals: Set<Long> = emptySet(),
    val video: Int = 5,
    val place: Int = 15,
    val only: Boolean = false,
)

data class Clash(val a: Meeting, val b: Meeting) {
    val at get() = maxOf(a.start, b.start)
    val until get() = minOf(a.end, b.end)
}

enum class Step { Wait, Live, Heads, Done }

object Meets {
    const val MIN = 60_000L
    const val AHEAD = 36 * 3_600_000L
    const val SOON = 12 * 3_600_000L
    const val COVER = 60 * MIN
    const val LIVE = 10 * MIN
    const val LINGER = 5 * MIN
    const val LATE = "Running about 10 minutes late, sorry!"
    val leads = listOf(5, 10, 15, 30)

    private const val TAIL = "[^\\s<>\"']"
    private const val EDGE = "(?:https?://|(?<![^\\s<>\"'(\\[]))"
    private val subs = "(?:[a-z0-9-]+\\.)*"
    private val sites = listOf(
        Via.Meet to "meet\\.google\\.com/[a-z]{3}-[a-z]{4}-[a-z]{3}(?![\\w-])(?:\\?$TAIL*)?",
        Via.Zoom to "${subs}zoom\\.us/(?:j/[0-9]+|my/[\\w.-]+)$TAIL*",
        Via.Teams to "(?:teams\\.microsoft\\.com/l/meetup-join/|teams\\.live\\.com/meet/)$TAIL+",
        Via.Webex to "${subs}webex\\.com/(?:meet/|join/|$TAIL*?j\\.php)$TAIL*",
    ).map { (v, p) -> v to Regex("(?i)$EDGE$p") }
    private val links = Regex("(?i)(?:https?://|www\\.)\\S+")
    private val scheme = Regex("(?i)^https?://")
    private val white = Regex("\\s+")
    private val virtual = Regex("(?i)^(?:(?:microsoft\\s+)?teams(?:\\s+meeting)?|zoom(?:\\s+meeting)?|google\\s+meet|meet|webex(?:\\s+meeting)?|online|virtual|video\\s*call|phone|tbd|n/?a)$")

    private fun link(raw: String) = raw.replace("&amp;", "&").trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '>').let { "https://" + it.replace(scheme, "") }

    private fun find(text: String): Join? =
        sites.mapNotNull { (v, r) -> r.find(text)?.let { it.range.first to Join(v, link(it.value)) } }.minByOrNull { it.first }?.second

    fun join(vararg texts: String?): Join? = texts.firstNotNullOfOrNull { t -> t?.let(::find) }

    fun place(location: String?): String? {
        val bare = sites.fold(location.orEmpty()) { t, (_, r) -> r.replace(t, " ") }
        val text = links.replace(bare, " ").replace(white, " ").trim(' ', ',', ';', '-')
        return text.takeIf { it.isNotEmpty() && !virtual.matches(it) }
    }

    fun wanted(m: Meeting, o: MeetOpts) = !o.only || m.join != null || m.place != null

    fun source(m: Meeting) = m.join?.via?.label ?: m.place

    fun lead(m: Meeting, o: MeetOpts) = if (m.join == null && m.place != null) o.place else o.video

    fun covered(lead: Int, reminders: List<Int>) = reminders.any { it in 0..lead }

    fun step(start: Long, lead: Int, now: Long) = when {
        now >= start + LINGER -> Step.Done
        now >= start - lead * MIN -> Step.Heads
        now >= start - LIVE -> Step.Live
        else -> Step.Wait
    }

    fun ongoing(start: Long, now: Long) = now >= start - LIVE

    fun mins(start: Long, now: Long) = ceil((start - now) / MIN.toDouble()).toLong().coerceAtLeast(0)

    fun until(start: Long, now: Long): String {
        val m = mins(start, now)
        return when {
            m == 0L -> "now"
            m < 60 -> "in $m min"
            m % 60 == 0L -> "in ${m / 60} h"
            else -> "in ${m / 60} h ${m % 60} min"
        }
    }

    fun chip(mins: Long) = if (mins <= 0) "now" else "in ${mins}m"

    fun clashes(all: List<Meeting>): List<Clash> {
        val s = all.sortedBy { it.start }
        return buildList {
            s.forEachIndexed { i, a ->
                for (b in s.drop(i + 1)) {
                    if (b.start >= a.end) break
                    if (a.title != b.title || a.start != b.start || a.end != b.end) add(Clash(a, b))
                }
            }
        }
    }

    fun clash(c: Clash, time: (Long) -> String) = "Two meetings overlap at ${time(c.at)}"
}
