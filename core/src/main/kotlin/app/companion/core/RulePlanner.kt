package app.companion.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

class RulePlanner(private val zone: ZoneId = ZoneId.of("Asia/Kolkata")) : Planner {
    private val cats = mapOf(
        Category.Food to "food khana khaana restaurant restaurants dining lunch dinner breakfast chai coffee snacks",
        Category.Groceries to "grocery groceries kirana sabzi ration",
        Category.Transport to "transport cab cabs auto petrol fuel metro commute",
        Category.Shopping to "shopping shop kapde",
        Category.Bills to "bills bill recharge rent kiraya electricity bijli",
        Category.Entertainment to "entertainment movie movies subscription subscriptions",
        Category.Health to "health medicine medicines dawai pharmacy doctor",
        Category.Travel to "travel flight flights train trains hotel hotels trip",
        Category.Transfer to "transfer transfers",
        Category.Income to "income salary earned",
    ).flatMap { (c, w) -> w.split(' ').map { it to c } }.toMap()

    private val brands = mapOf(
        "swiggy" to "Swiggy", "zomato" to "Zomato", "uber" to "Uber", "ola" to "Ola", "rapido" to "Rapido", "amazon" to "Amazon",
        "flipkart" to "Flipkart", "myntra" to "Myntra", "bigbasket" to "BigBasket", "blinkit" to "Blinkit", "zepto" to "Zepto",
        "netflix" to "Netflix", "spotify" to "Spotify", "airtel" to "Airtel", "jio" to "Jio", "irctc" to "IRCTC", "paytm" to "Paytm",
    )

    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val longMonths = setOf("january", "february", "march", "april", "june", "july", "august", "september", "sept", "october", "november", "december")
    private val stop = (
        "how much did i spend spent on in at the my total kitna kitne kharcha kharch kiya kiye ka ki ke pe par mein me for of " +
            "and to a an is are was what show list dikhao dikha batao bata give me all mera meri hai ho tha thi top biggest most sabse zyada " +
            "transactions transaction txns payments history card ending xx last past this week month year today yesterday kal aaj din days hua hue hui hafte " +
            "hafta mahine mahina pichle pichhle pichla previous current iss saal remind reminder add calendar event during"
        ).split(' ').toSet()

    override fun plan(text: String, now: Long): Plan {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val t = text.lowercase().replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
        write(text, t, now)?.let { return it }
        val words = t.split(' ').filter { it.isNotEmpty() }
        val has = { re: String -> Regex(re).containsMatchIn(t) }
        val money = has("\\b(spend|spent|spending|kharcha|kharch|total|how much|kitna|kitne|paid|expense|expenses|owe|owed|card|bill|bills|due|rs|inr)\\b") || '₹' in text
        val meal = has("\\b(had|ate|eaten|eat|khaya|khayi|khaye|piya|piyi|li|breakfast|lunch|dinner|brunch|snack|snacks|nashta|subah|raat)\\b") && !money
        if (meal) return Plan(emptyList(), false)
        if (has("\\bbills?\\b") && has("\\b(due|pending|upcoming|unpaid|baaki|payable|overdue)\\b")) {
            val (a, b) = billRange(t, today)
            return Plan(listOf(Query.ListBills(a, b, true)), true)
        }
        val range = range(t, today)
        val last4 = Regex("\\b(?:card|ending|xx|x)\\s*(\\d{4})\\b").find(t)?.groupValues?.get(1)
        val found = words.filter { it in brands }.distinct()
        val cat = words.firstNotNullOfOrNull { cats[it] }
        val shared = found.map { Category.of(brands.getValue(it)) }.distinct()
        val sameCat = found.size > 1 && shared.size == 1
        val merchant = when {
            sameCat -> null
            found.isNotEmpty() -> brands.getValue(found[0])
            else -> leftover(words)
        }
        val category = cat?.label ?: if (sameCat) shared[0].label else null
        val (start, end) = range.first to range.second
        val known = cat != null || found.isNotEmpty() || last4 != null || merchant != null
        val q: Query = when {
            has("\\b(top|biggest|most|sabse|zyada)\\b") && has("\\b(merchant|merchants|shops|kahan|where|spend|spends|kharcha)\\b") ->
                Query.TopMerchants(start, end, Regex("\\btop (\\d{1,2})\\b").find(t)?.groupValues?.get(1)?.toInt()?.coerceIn(1, 10) ?: 5)
            has("\\b(list|show|dikhao|dikha|transactions|txns|payments|history)\\b") && !has("\\bhow much\\b") ->
                Query.ListTxns(category, merchant, last4, start, end, Regex("\\b(?:last|top) (\\d{1,2}) (?:transactions|txns|payments)\\b").find(t)?.groupValues?.get(1)?.toInt()?.coerceIn(1, 50) ?: 10)
            else -> Query.SumSpend(category, merchant, last4, start, end)
        }
        val clear = q is Query.TopMerchants || merchant == null || found.isNotEmpty() || sameCat || resolves(merchant)
        return Plan(listOf(q), clear && (money || found.isNotEmpty() || known && range.third) || q is Query.TopMerchants)
    }

    private fun resolves(m: String) = Merchant.brand(m) != null || Category.of(m) != Category.Other

    private fun leftover(words: List<String>): String? {
        val rest = words.filter { it !in stop && it !in cats && !it.all(Char::isDigit) && it.length >= 3 && !isMonth(it) }
        return rest.takeIf { it.isNotEmpty() }?.joinToString(" ") { w -> w.replaceFirstChar(Char::uppercase) }
    }

    private fun isMonth(w: String) = w in longMonths || w in months

    private fun write(text: String, t: String, now: Long): Plan? {
        val remind = Regex("^(remind me( to)?|reminder( to)?|yaad dilao|yaad dila do)\\b").find(t)
        val cal = Regex("^(add|create|schedule|put|set)\\b.*\\b(calendar|event|meeting)\\b").containsMatchIn(t)
        if (remind == null && !cal) return null
        val none = Plan(emptyList(), false)
        val slot = Slots.find(text, now, zone) ?: return none
        val title = text.trim()
            .replace(Regex("(?i)^(remind me( to)?|reminder( to)?|yaad dilao|yaad dila do|add|create|schedule|put|set)\\s+"), "")
            .replace(Regex("(?i)\\b(to|on|in|my|the)?\\s*(calendar|event)\\b"), "")
            .replace(Regex("(?i)\\b(day after tomorrow|tomorrow|tmrw|today|tonight|next|mon|tue|wed|thu|fri|sat|sun)[a-z]*\\b"), "")
            .replace(Regex("(?i)\\b(at|@)?\\s*\\d{1,2}(:\\d{2})?\\s*(am|pm)\\b"), "")
            .replace(Regex("(?i)\\bin \\d+ ?(min|minutes|mins|hours|hrs|hour)\\b"), "")
            .replace(Regex("\\s+"), " ").trim().trimEnd('.', ',', '?').trim()
        if (title.isBlank()) return none
        val name = title.replaceFirstChar(Char::uppercase)
        val start = slot.millis(zone, LocalTime.of(9, 0))
        return Plan(listOf(if (remind != null) Query.CreateReminder(name, start) else Query.CreateEvent(name, start, start + 3600_000L)), true)
    }

    private fun monthRange(ym: YearMonth) = ym.atDay(1) to ym.atEndOfMonth()

    private fun billRange(t: String, today: LocalDate): Pair<LocalDate, LocalDate> {
        val n = Regex("\\bnext (\\d{1,3}) (?:days|din)\\b").find(t)?.groupValues?.get(1)?.toLong()
        return when {
            n != null -> today to today.plusDays(n)
            Regex("\\bnext (month|mahine)\\b").containsMatchIn(t) -> YearMonth.from(today).plusMonths(1).let { it.atDay(1) to it.atEndOfMonth() }
            Regex("\\b(this|is|iss) (month|mahine)\\b").containsMatchIn(t) -> today to YearMonth.from(today).atEndOfMonth()
            Regex("\\b(this|is|iss) (week|hafte)\\b").containsMatchIn(t) -> today to today.with(DayOfWeek.SUNDAY)
            Regex("\\bnext (week|hafte)\\b").containsMatchIn(t) -> today to today.plusDays(7)
            else -> today to today.plusDays(30)
        }
    }

    private fun range(t: String, today: LocalDate): Triple<LocalDate, LocalDate, Boolean> {
        fun r(a: LocalDate, b: LocalDate) = Triple(a, b, true)
        fun month(ym: YearMonth) = r(ym.atDay(1), ym.atEndOfMonth())
        Regex("\\b(?:past|last|pichle|pichhle|previous) (\\d{1,3}) (days|day|din|weeks|week|hafte|months|month|mahine)\\b").find(t)?.let { m ->
            val n = m.groupValues[1].toLong()
            return when (m.groupValues[2]) {
                "days", "day", "din" -> r(today.minusDays(n - 1), today)
                "weeks", "week", "hafte" -> r(today.minusDays(n * 7 - 1), today)
                else -> r(today.minusMonths(n).plusDays(1), today)
            }
        }
        if (Regex("\\b(last|past|previous|pichle|pichhle|pichla) (month|mahine|mahina)\\b").containsMatchIn(t)) return month(YearMonth.from(today).minusMonths(1))
        if (Regex("\\b(last|past|previous|pichle|pichhle|pichla) (week|hafte|hafta)\\b").containsMatchIn(t)) {
            val mon = today.with(DayOfWeek.MONDAY).minusWeeks(1)
            return r(mon, mon.plusDays(6))
        }
        if (Regex("\\b(last|previous|pichle|pichhle|pichla) (year|saal)\\b").containsMatchIn(t)) return r(LocalDate.of(today.year - 1, 1, 1), LocalDate.of(today.year - 1, 12, 31))
        if (Regex("\\b(this|is|iss|current) (week|hafte|hafta)\\b").containsMatchIn(t)) return r(today.with(DayOfWeek.MONDAY), today)
        if (Regex("\\b(this|is|iss|current) (month|mahine|mahina)\\b").containsMatchIn(t)) return r(today.withDayOfMonth(1), today)
        if (Regex("\\b(this|is|iss|current) (year|saal)\\b").containsMatchIn(t)) return r(today.withDayOfYear(1), today)
        if (Regex("\\b(yesterday|kal|beeta kal)\\b").containsMatchIn(t)) return r(today.minusDays(1), today.minusDays(1))
        if (Regex("\\b(today|aaj)\\b").containsMatchIn(t)) return r(today, today)
        val w = t.split(' ')
        for (i in w.indices) {
            val mo = months.indexOf(w[i].take(3)) + 1
            if (mo == 0 || !isMonth(w[i])) continue
            val year = w.getOrNull(i + 1)?.takeIf { Regex("20\\d\\d").matches(it) }?.toInt()
            val framed = w.getOrNull(i - 1) in setOf("in", "during") || w.getOrNull(i + 1) in setOf("mein", "me")
            if (w[i] in longMonths || year != null || framed) {
                return month(YearMonth.of(year ?: if (mo > today.monthValue) today.year - 1 else today.year, mo))
            }
        }
        return Triple(today.withDayOfMonth(1), today, false)
    }
}
