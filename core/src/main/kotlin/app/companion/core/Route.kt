package app.companion.core

object Route {
    private val period = Regex("\\b(today|yesterday|this week|last week|this month|last month|this year|last year|aaj|kal|\\w+ (?:weeks?|months?|days?) ago|last \\d+ days|(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)\\w*)\\b")
    private val filler = setOf("how", "much", "did", "i", "spend", "spent", "spending", "on", "at", "my", "the", "was", "what", "compare", "is", "of", "kharcha", "kitna", "this", "month", "week")
    private val ask = Regex("\\b(suggest|recommend|ideas?|options?|what (?:should|can|could) i (?:eat|have|cook|order)|what to (?:eat|cook|order)|kya (?:khau|khaun|khaoon|banau|banaun))\\b")

    const val SUGGEST = """{"tool":"suggest_meal","args":{}}"""

    fun asks(text: String) = ask.containsMatchIn(norm(text)) || text.trim().endsWith("?")

    fun of(text: String): String? {
        val t = norm(text)
        fun has(re: String) = Regex(re).containsMatchIn(t)
        fun day() = if (has("\\b(tomorrow|kal)\\b") && !has("\\byesterday\\b")) "tomorrow" else "today"
        return when {
            has("\\b(what|anything|kya) (?:needs?|need) (?:me|my attention|you)\\b|\\bneeds? (?:me|you)\\b|\\bpending items?\\b|\\bwhat(?:'s| is) pending\\b") -> call("needs_you")
            ask.containsMatchIn(t) && has("\\b(eat|meal|food|lunch|dinner|breakfast|snack|snacks|khana|cook|protein|kcal|calories?|veg|vegetarian|diet)\\b|kya khau|kya banau") ->
                call("suggest_meal", listOfNotNull(
                    Regex("(\\d{2,4})\\s*(?:kcal|cal|cals|calories?)").find(t)?.groupValues?.get(1)?.let { "\"kcal\":$it" },
                    if (has("\\b(veg|vegetarian|veggie|no meat|without meat)\\b") && !has("\\bnon ?veg")) "\"veg\":true" else null,
                ))
            has("\\s(?:vs|versus|compared (?:to|with))\\s") -> compare(t)
            has("\\b(best card|which card|card (?:to|should i) use|card for this)\\b") -> call("best_card")
            has("\\b(meetings?|calls?|calendar|schedule)\\b") && has("\\b(today|tomorrow|aaj|kal|next|upcoming|any)\\b") -> call("meetings", listOf("\"day\":\"${day()}\""))
            has("\\bweight\\b") && has("\\b(trend|progress|how|graph|chart|going|change)\\b") && !has("\\d+(?:\\.\\d)?\\s*(?:kg|kgs|kilo)") -> call("weight_trend")
            has("\\b(my|all) trips\\b|\\btrips so far\\b|\\blist trips\\b") -> call("trips")
            has("\\bwhat did i (?:eat|have)\\b|\\bmeals? (?:today|yesterday)\\b|\\bwhat i ate\\b") -> call("meals", listOf("\"date\":\"${if (has("\\byesterday\\b")) "yesterday" else "today"}\""))
            has("\\b(calories|kcal|protein)\\b") && has("\\b(today|so far|left|remaining|aaj)\\b") && !has("\\b(had|ate|khaya|khaye)\\b") -> call("food_today", listOf("\"date\":\"today\""))
            else -> null
        }
    }

    private fun compare(t: String): String? {
        val (a, b) = t.split(Regex("\\s(?:vs|versus|compared (?:to|with))\\s"), limit = 2).map { it.trim() }
        val pb = period.find(b)?.value ?: return null
        val pa = period.find(a)?.value ?: "this month"
        val subj = a.split(' ').filter { it !in filler && !period.matches(it) }.joinToString(" ").trim()
        val cat = Category.entries.firstOrNull { it != Category.Other && it.label == subj }
        val extra = when {
            subj.isEmpty() || period.containsMatchIn(subj) -> null
            cat != null -> "\"category\":\"${cat.label}\""
            else -> "\"merchant\":${Json.write(subj.split(' ').joinToString(" ") { w -> w.replaceFirstChar(Char::uppercase) })}"
        }
        return call("compare", listOfNotNull("\"period_a\":\"$pa\"", "\"period_b\":\"$pb\"", extra))
    }

    private fun call(tool: String, args: List<String> = emptyList()) = "{\"tool\":\"$tool\",\"args\":{${args.joinToString(",")}}}"

    private fun norm(s: String) = s.lowercase().replace('’', '\'').replace(Regex("[^a-z0-9'.\\s]"), " ").replace(Regex("\\s+"), " ").trim()
}
