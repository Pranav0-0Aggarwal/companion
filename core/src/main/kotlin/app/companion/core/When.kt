package app.companion.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class Meal(val label: String) {
    Breakfast("breakfast"), Lunch("lunch"), Snacks("snacks"), Dinner("dinner");

    companion object {
        private val named = mapOf(
            "breakfast" to Breakfast, "bfast" to Breakfast, "brekfast" to Breakfast, "nashta" to Breakfast, "naashta" to Breakfast, "nashte" to Breakfast, "nasta" to Breakfast,
            "lunch" to Lunch, "lunchtime" to Lunch,
            "snack" to Snacks, "snacks" to Snacks, "teatime" to Snacks,
            "dinner" to Dinner, "supper" to Dinner, "dinnertime" to Dinner,
        )
        private val times = mapOf(
            "morning" to Breakfast, "subah" to Breakfast, "subha" to Breakfast, "savere" to Breakfast,
            "noon" to Lunch, "afternoon" to Lunch, "dopahar" to Lunch, "dupahar" to Lunch, "dophar" to Lunch, "dopehar" to Lunch,
            "evening" to Snacks, "shaam" to Snacks, "sham" to Snacks,
            "night" to Dinner, "tonight" to Dinner, "raat" to Dinner, "midnight" to Dinner,
        )

        fun of(t: LocalTime): Meal = when {
            t.hour < 4 -> Dinner
            t.hour < 11 -> Breakfast
            t.hour < 15 -> Lunch
            t.hour < 19 -> Snacks
            else -> Dinner
        }

        fun of(hour: Int, minute: Int = 0): Meal = of(LocalTime.of(hour, minute))

        fun parse(text: String): Meal? {
            val w = Words.norm(text).split(' ')
            return w.firstNotNullOfOrNull { named[it] } ?: w.firstNotNullOfOrNull { times[it] }
        }

        fun label(s: String): Meal? = entries.firstOrNull { it.label == s }
    }
}

object Meals {
    val defaults: Map<Meal, LocalTime> = mapOf(
        Meal.Breakfast to LocalTime.of(9, 0),
        Meal.Lunch to LocalTime.of(13, 30),
        Meal.Snacks to LocalTime.of(17, 30),
        Meal.Dinner to LocalTime.of(21, 0),
    )
}

data class Wen(val day: LocalDate, val meal: Meal, val at: Long)

object When {
    private val weekdays = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY, "somvar" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "mangalvar" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY, "budhvar" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY, "guruvar" to DayOfWeek.THURSDAY, "veervar" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY, "shukravar" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY, "shanivar" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY, "ravivar" to DayOfWeek.SUNDAY,
    )

    private val justNow = Regex("\\b(?:just now|right now|now|abhi)\\b")
    private val rel = Regex("\\b(\\S+)\\s+(hours?|hrs?|ghante|ghanta|minutes?|mins?)\\s+(?:ago|pehle|pahle)\\b")
    private val dayBefore = Regex("\\bday before yesterday\\b|\\bparson?\\b")
    private val agoRe = Regex("\\b(\\S+)\\s+(?:days?|din|dino)\\s+(?:ago|pehle|pahle)\\b")
    private val lastPart = Regex("\\blast\\s+(?=(?:night|evening|morning|afternoon)\\b)")
    private val last = Regex("\\blast\\s+(\\w+)\\b")
    private val yest = Regex("\\byesterday\\b|\\bkal\\b|\\b(?:beeta|bita) kal\\b")
    private val todayRe = Regex("\\b(?:today|aaj|tonight|this)\\b")
    private val weekday = Regex("\\b(?:on\\s+)?(" + weekdays.keys.joinToString("|") + ")\\b")
    private val clock = Regex("\\b(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b")
    private val colon = Regex("\\b(\\d{1,2}):(\\d{2})\\b")
    private val bare = Regex("\\b(?:at|around)\\s+(\\d{1,2})\\b|\\b(\\d{1,2})\\s+baje\\b")

    fun resolve(text: String, now: Long, zone: ZoneId, times: Map<Meal, LocalTime> = Meals.defaults): Wen? {
        val nz = Instant.ofEpochMilli(now).atZone(zone)
        val today = nz.toLocalDate()
        var t = " " + text.lowercase().replace(Regex("([ap])\\.m\\.?"), "$1m").replace("half an hour", "30 minutes").replace("aadha ghanta", "30 minutes").replace(Regex("[^a-z0-9: ]+"), " ") + " "
        if (t.isBlank()) return Wen(today, Meal.of(nz.toLocalTime()), now)

        fun cut(r: Regex): MatchResult? = r.find(t)?.also { t = t.replaceRange(it.range, " ") }

        rel.find(t)?.let { m ->
            val q = Portion.qty(m.groupValues[1]) ?: return null
            val z = nz.minusSeconds((q.coerceAtMost(720.0) * if (m.groupValues[2].startsWith("m")) 60 else 3600).toLong())
            return Wen(z.toLocalDate(), Meal.of(z.toLocalTime()), z.toInstant().toEpochMilli())
        }
        if (justNow.containsMatchIn(t)) return Wen(today, Meal.parse(t) ?: Meal.of(nz.toLocalTime()), now)

        var off: Int? = null
        if (cut(dayBefore) != null) {
            off = 2
        } else {
            cut(agoRe)?.let { off = Portion.qty(it.groupValues[1])?.coerceIn(0.0, 365.0)?.toInt() ?: return null }
        }
        if (off == null && cut(lastPart) != null) off = 1
        if (off == null) last.findAll(t).firstOrNull { it.groupValues[1] in weekdays }?.let { m ->
            t = t.replaceRange(m.range, " ")
            off = past(today, weekdays.getValue(m.groupValues[1]), true)
        }
        if (off == null && cut(yest) != null) off = 1
        if (off == null) cut(weekday)?.let { off = past(today, weekdays.getValue(it.groupValues[1]), false) }
        if (off == null && todayRe.containsMatchIn(t)) off = 0

        var hm: Triple<Int, Int, String?>? = null
        cut(clock)?.let { hm = Triple(it.groupValues[1].toInt(), it.groupValues[2].ifEmpty { "0" }.toInt(), it.groupValues[3]) }
        if (hm == null) cut(colon)?.let { hm = Triple(it.groupValues[1].toInt(), it.groupValues[2].toInt(), null) }
        if (hm == null) cut(bare)?.let { hm = Triple((it.groupValues[1] + it.groupValues[2]).toInt(), 0, null) }
        hm = hm?.takeIf { (h, m, ap) -> m < 60 && h < 24 && (ap == null || h in 1..12) }

        val slot = Meal.parse(t)
        if (off == null && slot == null && hm == null) return null

        var day = today.minusDays((off ?: 0).toLong())
        val tod = hm?.let { (h, m, ap) -> LocalTime.of(hour(h, m, ap, slot, day == today, nz.toLocalTime()), m) }
        val meal = slot ?: tod?.let(Meal::of) ?: Meal.of(nz.toLocalTime())
        val at = tod ?: times[meal] ?: Meals.defaults.getValue(meal)
        fun ms() = day.atTime(at).atZone(zone).toInstant().toEpochMilli()
        val roll = if (tod != null) off == null else (off ?: 0) == 0 && meal >= Meal.Snacks && nz.hour < 4
        if (roll && ms() > now) day = day.minusDays(1)
        return Wen(day, meal, minOf(ms(), now))
    }

    private fun past(today: LocalDate, d: DayOfWeek, strict: Boolean): Int {
        val n = (today.dayOfWeek.value - d.value + 7) % 7
        return if (strict && n == 0) 7 else n
    }

    private fun hour(h: Int, m: Int, ap: String?, slot: Meal?, isToday: Boolean, now: LocalTime): Int = when {
        ap == "am" -> h % 12
        ap == "pm" -> h % 12 + 12
        h == 0 || h > 12 -> h
        slot == Meal.Dinner || slot == Meal.Snacks -> h % 12 + 12
        slot == Meal.Breakfast -> h % 12
        slot == Meal.Lunch -> if (h in 1..6) h + 12 else h
        isToday -> if (!LocalTime.of(h % 12 + 12, m).isAfter(now)) h % 12 + 12 else h % 12
        else -> if (h in 7..11) h else h % 12 + 12
    }
}
