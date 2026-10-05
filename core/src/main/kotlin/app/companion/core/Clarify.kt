package app.companion.core

sealed interface Answer {
    data class Kcal(val v: Double) : Answer
    data object Estimate : Answer
    data class Size(val s: String) : Answer
}

object Clarify {
    private val num = Regex("(?i)^(?:about|around|approx\\.?|~)?\\s*(\\d{2,4})\\s*(?:k?cal|calories)?[.!]*$")
    private val yes = Regex("(?i)^(?:yes|yeah|yep|ok|okay|haan|han|sure|estimate|go ahead|theek hai)\\b")
    private val split = Regex("\\s*(?:,|\\+|&|\\band\\b|\\baur\\b)\\s*", RegexOption.IGNORE_CASE)
    private const val MAX = 12

    fun parse(text: String, size: Boolean): Answer? {
        val t = text.trim()
        if (size) return Portion.size(t)?.let(Answer::Size)
        num.find(t)?.groupValues?.get(1)?.toDouble()?.takeIf { it in 5.0..5000.0 }?.let { return Answer.Kcal(it) }
        return if (yes.containsMatchIn(t)) Answer.Estimate else null
    }

    fun spoken(text: String): List<Req> = text.split(split).map { it.trim() }.filter { it.isNotEmpty() }.take(MAX).mapNotNull { p ->
        val w = p.split(' ').filter { it.isNotEmpty() }
        val q = Portion.qty(w[0])
        val rest = if (q != null) w.drop(1) else w
        val u = rest.firstOrNull()?.let(Portion::unit)
        val name = (if (u != null) rest.drop(1) else rest).joinToString(" ").removePrefix("of ").trim()
        if (name.isEmpty()) null else Req(name, qty = q ?: 1.0, unit = u)
    }
}
