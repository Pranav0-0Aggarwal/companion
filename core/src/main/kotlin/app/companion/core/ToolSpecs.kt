package app.companion.core

data class ToolSpec(val name: String, val desc: String, val args: List<Arg> = emptyList())

object ToolSpecs {
    val screens = listOf("today", "money", "food", "inbox", "you", "ledger", "bills", "cards", "trips", "vault", "learn", "settings")

    val spend = ToolSpec(
        "spend", "Money spent in a period, optionally compared.",
        listOf(
            Arg("period", Ty.Str, desc = "period as said, like last month or this week"),
            Arg("category", Ty.Pick(Category.entries.map { it.label }), false),
            Arg("merchant", Ty.Str, false),
            Arg("compare", Ty.Str, false, "second period to compare with"),
        ),
    )
    val bills = ToolSpec("bills", "Bills due in a date range.", listOf(Arg("range", Ty.Str, desc = "range as said, like this week or next month"), Arg("unpaid_only", Ty.Bool, false)))
    val cards = ToolSpec("cards", "Credit cards with statement and due days.")
    val balance = ToolSpec("balance", "Account balance. Not available yet.", listOf(Arg("account", Ty.Str, false)))
    val logMeal = ToolSpec(
        "log_meal", "Log a meal eaten now or earlier.",
        listOf(
            Arg(
                "items",
                Ty.Arr(
                    Ty.Obj(
                        listOf(
                            Arg("name", Ty.Str),
                            Arg("brand", Ty.Str, false),
                            Arg("qty", Ty.Num, false),
                            Arg("unit", Ty.Str, false, "piece, bowl, plate, cup, g, ml"),
                            Arg("size", Ty.Str, false, "small, medium or large"),
                            Arg("mods", Ty.Arr(Ty.Str), false, "customisations like extra cheese"),
                        ),
                    ),
                ),
            ),
            Arg("meal", Ty.Pick(Meal.entries.map { it.label }), false),
            Arg("when", Ty.Str, false),
        ),
    )
    val foodToday = ToolSpec("food_today", "Calories eaten on a day against goal.", listOf(Arg("date", Ty.Str, false)))
    val setKcal = ToolSpec(
        "set_kcal", "Remember a food's calories per serving.",
        listOf(Arg("item_key", Ty.Str), Arg("kcal", Ty.Num), Arg("protein", Ty.Num, false), Arg("carbs", Ty.Num, false), Arg("fat", Ty.Num, false)),
    )
    val logWeight = ToolSpec("log_weight", "Log weight in kilograms.", listOf(Arg("kg", Ty.Num), Arg("when", Ty.Str, false)))
    val startTrip = ToolSpec("start_trip", "Start a trip to tag spends.", listOf(Arg("name", Ty.Str), Arg("from", Ty.Str, false), Arg("to", Ty.Str, false)))
    val endTrip = ToolSpec("end_trip", "End the active trip.")
    val tripSummary = ToolSpec("trip_summary", "Spending summary of a trip.", listOf(Arg("name", Ty.Str, false)))
    val vaultFind = ToolSpec("vault_find", "Find saved documents by kind or title.", listOf(Arg("query", Ty.Str)))
    val vaultAdd = ToolSpec(
        "vault_add", "Save a document with number and expiry.",
        listOf(
            Arg("kind", Ty.Pick(DocKind.entries.map { it.name })),
            Arg("title", Ty.Str, false),
            Arg("number", Ty.Str, false),
            Arg("expires", Ty.Str, false, "date as said or yyyy-mm-dd"),
            Arg("note", Ty.Str, false),
        ),
    )
    val remind = ToolSpec("remind", "Set a reminder.", listOf(Arg("text", Ty.Str), Arg("when", Ty.Str)))
    val calendarAdd = ToolSpec("calendar_add", "Add a calendar event.", listOf(Arg("title", Ty.Str), Arg("start", Ty.Str), Arg("end", Ty.Str, false)))
    val open = ToolSpec("open", "Open a screen of the app.", listOf(Arg("screen", Ty.Pick(screens))))

    private val categories = Ty.Pick(Category.entries.map { it.label })

    val searchMessages = ToolSpec("search_messages", "Search messages and notifications.", listOf(Arg("q", Ty.Str), Arg("from", Ty.Str, false, "sender name"), Arg("days", Ty.Int, false, "look back this many days")))
    val ledger = ToolSpec(
        "ledger", "List spends with a total.",
        listOf(Arg("merchant", Ty.Str, false), Arg("category", categories, false), Arg("card", Ty.Str, false, "bank or last four"), Arg("min", Ty.Num, false, "rupees"), Arg("max", Ty.Num, false, "rupees"), Arg("period", Ty.Str, false, "period as said")),
    )
    val topMerchants = ToolSpec("top_merchants", "Top merchants by spend.", listOf(Arg("period", Ty.Str, false, "period as said"), Arg("n", Ty.Int, false)))
    val compare = ToolSpec(
        "compare", "Compare spending across two periods.",
        listOf(Arg("period_a", Ty.Str), Arg("period_b", Ty.Str), Arg("category", categories, false), Arg("merchant", Ty.Str, false)),
    )
    val needsYou = ToolSpec("needs_you", "Inbox items waiting for you.", listOf(Arg("n", Ty.Int, false)))
    val billCycle = ToolSpec("bill_cycle", "Bill amount, due date and paid status.", listOf(Arg("card", Ty.Str, false, "bank or last four"), Arg("biller", Ty.Str, false)))
    val bestCard = ToolSpec("best_card", "Card with the longest interest free window.")
    val meals = ToolSpec("meals", "Meals eaten on a day.", listOf(Arg("date", Ty.Str, false)))
    val weightTrend = ToolSpec("weight_trend", "Weight trend over recent weeks.", listOf(Arg("weeks", Ty.Int, false)))
    val meetings = ToolSpec("meetings", "Calendar meetings on a day.", listOf(Arg("day", Ty.Str, false)))
    val trips = ToolSpec("trips", "All trips with their spend.")

    val markPaid = ToolSpec("mark_paid", "Mark a bill paid, after confirm.", listOf(Arg("bill", Ty.Str, desc = "item id or bill name")))
    val file = ToolSpec("file", "File a spend under a category.", listOf(Arg("item", Ty.Str, desc = "item id like i:12"), Arg("category", categories)))
    val retype = ToolSpec("retype", "Change what kind an item is.", listOf(Arg("item", Ty.Str, desc = "item id like i:12"), Arg("label", Ty.Pick(Labels.pick))))
    val markDup = ToolSpec("mark_dup", "Mark an item as a duplicate.", listOf(Arg("item", Ty.Str, desc = "the duplicate id"), Arg("keep", Ty.Str, desc = "the id to keep")))
    val renameMerchant = ToolSpec("rename_merchant", "Rename a merchant everywhere.", listOf(Arg("from", Ty.Str), Arg("to", Ty.Str)))
    val notSpending = ToolSpec("not_spending", "Stop counting a merchant as spending.", listOf(Arg("merchant", Ty.Str)))
    val addCard = ToolSpec("add_card", "Add a card found in messages.", listOf(Arg("bank", Ty.Str), Arg("last4", Ty.Str, desc = "last four digits")))
    val setBudget = ToolSpec("set_budget", "Set the monthly budget in rupees.", listOf(Arg("amount", Ty.Num)))
    val dismiss = ToolSpec("dismiss", "Dismiss an inbox item.", listOf(Arg("item", Ty.Str, desc = "item id like i:12")))

    val all = listOf(
        spend, bills, cards, balance, logMeal, foodToday, setKcal, logWeight, startTrip, endTrip, tripSummary, vaultFind, vaultAdd, remind, calendarAdd, open,
        searchMessages, ledger, topMerchants, compare, needsYou, billCycle, bestCard, meals, weightTrend, meetings, trips,
        markPaid, file, retype, markDup, renameMerchant, notSpending, addCard, setBudget, dismiss,
    )
}
