package app.companion.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

sealed interface Suggestion {
    data class Remind(val title: String, val at: Long) : Suggestion
    data class Cal(val title: String, val start: Long, val end: Long, val allDay: Boolean, val note: String) : Suggestion
    data class Todo(val title: String) : Suggestion
}

object Suggest {
    private val ref = Regex("(?i)(?:pnr|booking\\s*(?:id|ref(?:erence)?|no\\.?)|confirmation\\s*(?:code|no\\.?|number)|ref(?:erence)?\\s*(?:no\\.?|number|id))\\s*[:#-]?\\s*([A-Z0-9]{5,12})")
    private val hour = 60 * 60 * 1000L

    fun ref(text: String): String? = ref.find(text)?.groupValues?.get(1)

    fun bill(title: String, due: LocalDate, now: Long, zone: ZoneId): List<Suggestion.Remind> =
        listOf(due.minusDays(2), due)
            .map { Suggestion.Remind(title, Slot(it, LocalTime.of(9, 0)).millis(zone)) }
            .filter { it.at > now }

    fun travel(e: Event.Travel, text: String, now: Long, zone: ZoneId): Suggestion.Cal? {
        val date = e.date ?: return null
        val t = Slots.find(text, now, zone)?.takeIf { it.date == date }?.time
        val code = ref(text)
        val note = code?.let { "Ref $it" }.orEmpty()
        val title = "${e.what} booking"
        return if (t != null) {
            val s = Slot(date, t).millis(zone)
            Suggestion.Cal(title, s, s + 2 * hour, false, note)
        } else {
            val s = Slot(date, null).millis(zone, LocalTime.MIDNIGHT)
            Suggestion.Cal(title, s, s + 24 * hour, true, note)
        }
    }

    fun chat(sender: String, text: String, now: Long, zone: ZoneId): Suggestion.Cal? {
        val slot = Slots.find(text, now, zone) ?: return null
        val t = slot.time ?: return null
        val s = Slot(slot.date, t).millis(zone)
        return Suggestion.Cal("Plan with $sender", s, s + hour, false, "")
    }

    fun shared(text: String, now: Long, zone: ZoneId): Suggestion {
        val title = text.trim().lineSequence().first().take(120)
        val slot = Slots.find(text, now, zone) ?: return Suggestion.Todo(title)
        val t = slot.time ?: return Suggestion.Remind(title, slot.millis(zone))
        val s = Slot(slot.date, t).millis(zone)
        return Suggestion.Cal(title, s, s + hour, false, "")
    }
}
