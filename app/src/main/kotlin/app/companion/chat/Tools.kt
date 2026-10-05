package app.companion.chat

import android.content.Context
import app.companion.core.Card
import app.companion.core.Tool
import app.companion.core.ToolSpec
import app.companion.data.Repo

internal fun Map<String, Any?>.s(k: String) = (this[k] as? String)?.trim()?.takeIf { it.isNotEmpty() }

internal fun Map<String, Any?>.d(k: String) = (this[k] as? Number)?.toDouble()

internal fun Map<String, Any?>.b(k: String) = this[k] as? Boolean

@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.l(k: String) = (this[k] as? List<Any?>).orEmpty()

@Suppress("UNCHECKED_CAST")
internal fun Any?.m() = this as? Map<String, Any?>

abstract class Base(private val s: ToolSpec) : Tool {
    override val name get() = s.name
    override val desc get() = s.desc
    override val args get() = s.args
}

object ChatTools {
    fun of(c: Context, repo: Repo, foods: Foods, gate: Gate, go: (String) -> Unit, show: (Card) -> Unit): List<Tool> = listOf(
        Spend(repo, show), Bills(repo, show), Cards(repo), Balance(),
        LogMeal(foods), FoodToday(foods), SetKcal(repo), LogWeight(c, repo, show),
        StartTrip(repo, show), EndTrip(repo, show), TripSummaryTool(repo, show),
        VaultFind(repo, show), VaultAdd(repo),
        Remind(c), CalendarAdd(c, repo), Open(go),
        SearchMessages(repo, show), Ledger(repo, show), TopMerchants(repo, show), Compare(repo, show), NeedsYou(repo, show),
        BillCycle(repo, show), BestCard(repo, show), Meals(repo, foods, show), WeightTrend(repo, show), Meetings(c, show), Trips(repo, show),
        MarkPaid(repo, gate, show), FileSpend(repo, gate, show), Retype(repo, gate, show), MarkDup(repo, gate, show), RenameMerchant(repo, gate, show),
        NotSpending(repo, gate, show), AddCard(repo, gate, show), SetBudget(gate, show), Dismiss(repo, gate, show),
    )
}
