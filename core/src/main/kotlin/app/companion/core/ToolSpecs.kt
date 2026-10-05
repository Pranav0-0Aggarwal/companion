package app.companion.core

data class ToolSpec(val name: String, val desc: String, val args: List<Arg> = emptyList())

object ToolSpecs {
    val screens = listOf("today", "money", "food", "inbox", "you", "ledger", "bills", "cards", "trips", "vault", "learn", "settings")

    val spend = ToolSpec(
        "spend", "Money spent in a period, optionally for a category or merchant, optionally compared with a second period.",
        listOf(
            Arg("period", Ty.Str, desc = "period as said, like last month or this week"),
            Arg("category", Ty.Pick(Category.entries.map { it.label }), false),
            Arg("merchant", Ty.Str, false),
            Arg("compare", Ty.Str, false, "second period to compare with"),
        ),
    )
    val bills = ToolSpec("bills", "Bills due in a date range.", listOf(Arg("range", Ty.Str, desc = "range as said, like this week or next month"), Arg("unpaid_only", Ty.Bool, false)))
    val cards = ToolSpec("cards", "The user's credit cards with statement and due days.")
    val balance = ToolSpec("balance", "Balance of an account. Not available yet.", listOf(Arg("account", Ty.Str, false)))
    val logMeal = ToolSpec(
        "log_meal", "Log what the user ate, now or in the past. when is a phrase like yesterday dinner or this morning.",
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
    val foodToday = ToolSpec("food_today", "What the user ate on a day with calories against the goal. date is a phrase like today or yesterday.", listOf(Arg("date", Ty.Str, false)))
    val setKcal = ToolSpec(
        "set_kcal", "Remember the calories of a food per serving so it is never asked again. item_key is the food name, or brand|name.",
        listOf(Arg("item_key", Ty.Str), Arg("kcal", Ty.Num), Arg("protein", Ty.Num, false), Arg("carbs", Ty.Num, false), Arg("fat", Ty.Num, false)),
    )
    val logWeight = ToolSpec("log_weight", "Log the user's weight in kilograms.", listOf(Arg("kg", Ty.Num), Arg("when", Ty.Str, false)))
    val startTrip = ToolSpec("start_trip", "Start a trip so spends in its dates are tagged to it. from and to are date phrases.", listOf(Arg("name", Ty.Str), Arg("from", Ty.Str, false), Arg("to", Ty.Str, false)))
    val endTrip = ToolSpec("end_trip", "End the active trip.")
    val tripSummary = ToolSpec("trip_summary", "Spending summary of a trip. Defaults to the active or latest trip.", listOf(Arg("name", Ty.Str, false)))
    val vaultFind = ToolSpec("vault_find", "Find saved documents by kind or title. Only masked numbers are shown; revealing needs the vault screen.", listOf(Arg("query", Ty.Str)))
    val vaultAdd = ToolSpec(
        "vault_add", "Save a document in the vault with its number and expiry date.",
        listOf(
            Arg("kind", Ty.Pick(DocKind.entries.map { it.name })),
            Arg("title", Ty.Str, false),
            Arg("number", Ty.Str, false),
            Arg("expires", Ty.Str, false, "date as said or yyyy-mm-dd"),
            Arg("note", Ty.Str, false),
        ),
    )
    val remind = ToolSpec("remind", "Set a reminder. when is a date and time phrase as said.", listOf(Arg("text", Ty.Str), Arg("when", Ty.Str)))
    val calendarAdd = ToolSpec("calendar_add", "Add a calendar event. start and end are date and time phrases as said.", listOf(Arg("title", Ty.Str), Arg("start", Ty.Str), Arg("end", Ty.Str, false)))
    val open = ToolSpec("open", "Open a screen of the app.", listOf(Arg("screen", Ty.Pick(screens))))

    val all = listOf(spend, bills, cards, balance, logMeal, foodToday, setKcal, logWeight, startTrip, endTrip, tripSummary, vaultFind, vaultAdd, remind, calendarAdd, open)
}
