package app.companion.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Needle {
    const val INSTRUCTION = "Copy names, merchants, titles and date phrases verbatim; never resolve dates. Map each explicit supported request to exactly one declared call. Unsupported or unclear requests return no call."

    private const val CATS = """"enum":["food","groceries","shopping","transport","travel","bills","entertainment","health","transfer","other"]"""
    private const val PERIOD = """"period":{"type":"string","minLength":1,"maxLength":60,"description":"the date or period phrase copied word for word, e.g. 'last month', 'this week', 'Q2', 'pichle hafte'"}"""
    private const val WHEN = """"when":{"type":"string","minLength":1,"maxLength":60,"description":"the date and time phrase copied word for word, e.g. 'tomorrow at 9am', 'Friday 5pm', 'kal subah 9 baje'"""
    private const val FILTERS = """"category":{"type":"string",$CATS},"merchant":{"type":"string","description":"merchant name as said"},"card_last4":{"type":"string","pattern":"^[0-9]{4}${'$'}","description":"last 4 digits of the card"}"""

    val TOOLS = "[" + listOf(
        """{"name":"sum_spend","description":"Total money spent in a date range, optionally filtered by category, merchant or card.","parameters":{"type":"object","properties":{$FILTERS,$PERIOD},"required":["period"]}}""",
        """{"name":"list_transactions","description":"List individual transactions, optionally filtered by category, merchant, card or date range.","parameters":{"type":"object","properties":{$FILTERS,"limit":{"type":"integer","minimum":1,"maximum":50,"default":10,"description":"how many to show"},$PERIOD}}}""",
        """{"name":"list_bills","description":"List bills by due date period, optionally only unpaid ones.","parameters":{"type":"object","properties":{"unpaid_only":{"type":"boolean","default":false,"description":"true only for pending, due or unpaid bills"},$PERIOD},"required":["period"]}}""",
        """{"name":"top_merchants","description":"Merchants where the most money was spent in a date range.","parameters":{"type":"object","properties":{"n":{"type":"integer","minimum":1,"maximum":20,"default":5,"description":"how many merchants"},$PERIOD},"required":["period"]}}""",
        """{"name":"create_reminder","description":"Create a reminder for a title at a date and time.","parameters":{"type":"object","properties":{"title":{"type":"string","description":"what to be reminded of, copied from the request"},$WHEN"}},"required":["title","when"]}}""",
        """{"name":"create_event","description":"Add a calendar event with a start time and optional end time.","parameters":{"type":"object","properties":{"title":{"type":"string","description":"event name, copied from the request"},$WHEN including any end time"}},"required":["title","when"]}}""",
    ).joinToString(",") + "]"

    private val day = DateTimeFormatter.ofPattern("yyyy-MM-dd EEE", Locale.ENGLISH)

    fun system(now: Long, zone: ZoneId) =
        "date: ${day.format(Instant.ofEpochMilli(now).atZone(zone))}; locale: en-IN; location: ${zone.id}; $INSTRUCTION"
}

class NeedleCalls(private val now: Long, private val zone: ZoneId = ZoneId.of("Asia/Kolkata")) {
    private val check = Validator(now, zone)
    private val phrase = Phrase(now, zone)
    private val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

    fun parse(json: String): List<Query> {
        val root = runCatching { Json.parse(json) }.getOrNull() as? Map<*, *> ?: return emptyList()
        if (root["success"] != true) return emptyList()
        val calls = (root["function_calls"] as? List<*>)?.takeIf { it.size in 1..MAX } ?: return emptyList()
        val qs = calls.map { (it as? Map<*, *>)?.let(::call)?.let(check::check) ?: return emptyList() }
        return qs
    }

    private fun str(a: Map<*, *>, k: String) = (a[k] as? String)?.trim()?.takeIf { it.isNotEmpty() }

    private fun int(a: Map<*, *>, k: String, d: Int) = (a[k] as? Number)?.toInt() ?: d

    private fun call(c: Map<*, *>): Query? {
        val a = c["arguments"] as? Map<*, *> ?: emptyMap<Any, Any>()
        val cat = str(a, "category")?.lowercase()
        val merchant = str(a, "merchant")
        val card = str(a, "card_last4")
        val title = str(a, "title")
        val whenAt = str(a, "when")
        fun span(future: Boolean = false, absent: Pair<LocalDate, LocalDate>? = null) = str(a, "period")?.let { phrase.period(it, future) } ?: absent
        return when (c["name"]) {
            "sum_spend" -> span()?.let { Query.SumSpend(cat, merchant, card, it.first, it.second) }
            "list_transactions" -> span(absent = today.minusYears(1) to today)?.let { Query.ListTxns(cat, merchant, card, it.first, it.second, int(a, "limit", 10).coerceIn(1, 50)) }
            "list_bills" -> span(true)?.let { Query.ListBills(it.first, it.second, a["unpaid_only"] as? Boolean ?: false) }
            "top_merchants" -> span()?.let { Query.TopMerchants(it.first, it.second, int(a, "n", 5).coerceIn(1, 10)) }
            "create_reminder" -> Query.CreateReminder(title ?: return null, phrase.slot(whenAt ?: return null)?.millis(zone) ?: return null)
            "create_event" -> phrase.slot(whenAt ?: return null)?.millis(zone)?.let { Query.CreateEvent(title ?: return null, it, it + HOUR) }
            else -> null
        }
    }

    private companion object {
        const val MAX = 3
        const val HOUR = 3_600_000L
    }
}
