package app.companion.system

import android.content.Context
import app.companion.core.Eat
import app.companion.core.Quiet
import java.time.LocalTime

object FoodPrefs {
    private const val FILE = "food"
    private val OLD = Regex("n\\d+")

    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun slots(c: Context): Set<Eat> = Eat.entries.filter { p(c).getBoolean("slot_${it.name}", true) }.toSet()

    fun slot(c: Context, e: Eat, on: Boolean) = p(c).edit().putBoolean("slot_${e.name}", on).apply()

    fun weight(c: Context) = p(c).getBoolean("weight", true)

    fun weight(c: Context, on: Boolean) = p(c).edit().putBoolean("weight", on).apply()

    fun health(c: Context) = p(c).getBoolean("health", false)

    fun health(c: Context, on: Boolean) = p(c).edit().putBoolean("health", on).apply()

    fun quiet(c: Context): Quiet = p(c).let {
        Quiet(LocalTime.ofSecondOfDay(it.getInt("from", 22 * 60 + 30) * 60L), LocalTime.ofSecondOfDay(it.getInt("to", 7 * 60) * 60L))
    }

    fun quiet(c: Context, q: Quiet) = p(c).edit().putInt("from", q.from.hour * 60 + q.from.minute).putInt("to", q.to.hour * 60 + q.to.minute).apply()

    fun nudged(c: Context, day: Long): Set<Eat> = p(c).getString("n$day", "").orEmpty().split(',').mapNotNull { s -> Eat.entries.firstOrNull { it.name == s } }.toSet()

    fun nudge(c: Context, day: Long, e: Eat) {
        val s = p(c)
        s.edit().apply {
            s.all.keys.filter { it.matches(OLD) && it != "n$day" }.forEach(::remove)
            putString("n$day", (nudged(c, day) + e).joinToString(",") { it.name })
        }.apply()
    }

    fun weighed(c: Context) = p(c).getLong("weighed", -1L)

    fun weighed(c: Context, day: Long) = p(c).edit().putLong("weighed", day).apply()

    fun sure(c: Context): Set<Long> = p(c).getString("sure", "").orEmpty().split(',').mapNotNull(String::toLongOrNull).toSet()

    fun sure(c: Context, id: Long) = p(c).edit().putString("sure", (sure(c) + id).toList().takeLast(200).joinToString(",")).apply()
}
