package app.companion.chat

import android.content.Context
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
    fun of(c: Context, repo: Repo, foods: Foods, go: (String) -> Unit): List<Tool> = listOf(
        Spend(repo), Bills(repo), Cards(repo), Balance(),
        LogMeal(foods), FoodToday(foods), SetKcal(repo), LogWeight(c, repo),
        StartTrip(repo), EndTrip(repo), TripSummaryTool(repo),
        VaultFind(repo), VaultAdd(repo),
        Remind(c), CalendarAdd(c, repo), Open(go),
    )
}
