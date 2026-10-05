package app.companion.core

data class Bar(val label: String, val value: Long)

data class Row(val title: String, val sub: String, val paise: Long)

data class MealLine(val name: String, val qty: String, val kcal: Int, val est: Boolean)

data class DocLine(val id: Long, val kind: String, val title: String, val mask: String?, val days: Int?)

data class Fact(val title: String, val sub: String = "", val value: String = "")

sealed interface Card {
    data class Spend(val label: String, val total: Long, val count: Int, val bars: List<Bar>, val versus: Bar? = null) : Card
    data class Bills(val label: String, val total: Long, val rows: List<Row>) : Card
    data class Meal(val id: Long, val day: Long, val slot: String, val kcal: Int, val items: List<MealLine>, val dayKcal: Int, val goal: Int?) : Card
    data class Day(val day: Long, val kcal: Int, val goal: Int?, val meals: List<Bar>) : Card
    data class Weight(val kg: Double, val day: Long, val perWeek: Double?, val avg7: Double?) : Card
    data class Trip(val id: Long, val name: String, val total: Long, val perDay: Long, val days: Int, val top: List<Bar>) : Card
    data class Docs(val rows: List<DocLine>) : Card
    data class Facts(val label: String, val big: String?, val rows: List<Fact>, val more: Int = 0, val open: String? = null) : Card
    data class Confirm(val key: Long, val text: String) : Card
}

object Bars {
    fun fracs(bars: List<Bar>): List<Float> {
        val top = bars.maxOfOrNull { it.value }?.takeIf { it > 0 } ?: return bars.map { 0f }
        return bars.map { (it.value.toFloat() / top).coerceIn(0f, 1f) }
    }

    fun share(bars: List<Bar>): List<Float> {
        val sum = bars.sumOf { it.value }.takeIf { it > 0 } ?: return bars.map { 0f }
        return bars.map { it.value.toFloat() / sum }
    }
}

data class Opt(val label: String, val send: String?)

object ChatOpts {
    private val est = Regex("estimate (\\d{2,4})")
    private val size = Regex("(?i)^small, medium or large")

    fun of(question: String): List<Opt> = when {
        size.containsMatchIn(question) -> listOf("Small", "Medium", "Large").map { Opt(it, it) }
        else -> est.find(question)?.let { listOf(Opt("~${it.groupValues[1]} kcal (estimate)", "yes"), Opt("Enter value", null)) }.orEmpty()
    }
}

object Split {
    val presets = listOf(1.0, 0.5, 1.0 / 3)

    fun label(s: Double): String = when {
        s >= 0.999 -> "Full"
        kotlin.math.abs(s - 0.5) < 0.005 -> "1/2"
        kotlin.math.abs(s - 1.0 / 3) < 0.005 -> "1/3"
        kotlin.math.abs(s - 0.25) < 0.005 -> "1/4"
        else -> "${Math.round(s * 100)}%"
    }

    fun custom(percent: String): Double? = percent.trim().toIntOrNull()?.takeIf { it in 1..100 }?.let { it / 100.0 }
}
