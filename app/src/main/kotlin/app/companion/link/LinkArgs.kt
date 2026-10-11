package app.companion.link

object LinkArgs {
    fun item(q: Obj) = (q["item"] as? Number)?.toLong() ?: throw LinkBad("item")

    fun text(q: Obj, k: String, max: Int) = (q[k] as? String)?.take(max)?.takeIf { it.isNotBlank() } ?: throw LinkBad(k)

    fun days(q: Obj) = ((q["days"] as? Number)?.toInt() ?: 30).coerceIn(1, 92)

    fun at(q: Obj) = (q["remindAt"] as? Number)?.toLong()?.takeIf { it > 0 }
}
