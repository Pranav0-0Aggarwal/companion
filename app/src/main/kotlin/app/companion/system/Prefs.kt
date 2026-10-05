package app.companion.system

import android.content.Context
import app.companion.core.Opts

object Prefs {
    private const val FILE = "notify"
    private const val SINCE = "since"

    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun get(c: Context): Opts = p(c).let {
        Opts(
            it.getBoolean("spend", true), it.getBoolean("bill", true), it.getBoolean("remind", true), it.getBoolean("delivery", true),
            it.getBoolean("digest", true), it.getBoolean("lock", false), it.getBoolean("hide", false),
        )
    }

    fun set(c: Context, o: Opts) {
        p(c).edit()
            .putBoolean("spend", o.spend).putBoolean("bill", o.bill).putBoolean("remind", o.remind).putBoolean("delivery", o.delivery)
            .putBoolean("digest", o.digest).putBoolean("lock", o.lock).putBoolean("hide", o.hide)
            .apply()
    }

    fun since(c: Context): Long {
        val s = p(c)
        return s.getLong(SINCE, 0).takeIf { it > 0 } ?: (System.currentTimeMillis() - 60_000L).also { s.edit().putLong(SINCE, it).apply() }
    }

    fun clear(c: Context) {
        p(c).edit().clear().commit()
    }
}
