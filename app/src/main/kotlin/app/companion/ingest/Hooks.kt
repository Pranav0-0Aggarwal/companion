package app.companion.ingest

import android.content.Context
import app.companion.ai.DocIngest
import app.companion.chat.Foods
import app.companion.core.Eat
import app.companion.core.Meal
import app.companion.core.OrderLines
import app.companion.core.Raw
import app.companion.core.Source
import app.companion.core.Spend
import app.companion.core.Wen
import app.companion.core.text
import app.companion.data.Added
import app.companion.data.Item
import app.companion.data.money
import app.companion.data.Repo
import app.companion.system.FoodNotes
import app.companion.system.FoodPrefs
import app.companion.system.Nudges
import app.companion.ui.inr
import java.time.Instant
import java.time.ZoneId

class Hooks(private val c: Context, private val repo: Repo, private val foods: () -> Foods, private val docs: DocIngest) {
    private val apps = Regex("(?i)swiggy|zomato")
    private val shelf = Regex("(?i)blinkit|zepto|instamart|bigbasket|jiomart|dmart|grofers|milk|dairy|fruit|vegetable|kirana")
    private val zone get() = ZoneId.systemDefault()

    suspend fun after(raw: Raw, added: Added) {
        val i = added.item
        val text = raw.text()
        if (added.fresh && raw.source != Source.Wa && raw.source != Source.Ig && !i.money) docs.text(text, "msg")
        if (i.kind == "Debit" || i.kind == "CardSpend") {
            if (added.fresh) repo.life.tagLive(i, text)
            food(i, text)
        }
    }

    private suspend fun food(i: Item, text: String) {
        val merchant = i.merchant
        if (i.category != "food" || i.paise <= 0 || i.currency != "INR" || merchant == null || shelf.containsMatchIn(merchant)) return
        val lines = OrderLines.parse(text, merchant)
        val f = foods()
        val at = Instant.ofEpochMilli(i.at).atZone(zone)
        val w = Wen(at.toLocalDate(), Meal.of(at.toLocalTime()), i.at)
        val have = repo.life.orderAt(i.at)
        if (have != null) {
            if (lines.isNotEmpty() && repo.life.eaten(w.day).firstOrNull { it.meal.id == have.id }?.items.isNullOrEmpty()) f.quiet(lines, w, "order", merchant, have.id)
            return
        }
        if (lines.isNotEmpty()) {
            f.quiet(lines, w, "order", merchant, null)
            return
        }
        val id = repo.life.addBare(w, "order", merchant)
        Nudges.schedule(c)
        if (Eat.valueOf(w.meal.name) !in FoodPrefs.slots(c)) return
        val chain = f.chain(merchant) != null || apps.containsMatchIn(merchant)
        FoodNotes.order(c, id, if (chain) "${inr(i.paise)} at $merchant. What did you order?" else Spend.prompt(merchant, i.category, i.paise, false, false) ?: return)
    }
}
