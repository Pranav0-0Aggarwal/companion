package app.companion.system

import android.content.Context
import app.companion.core.BriefOpts
import app.companion.core.MeetOpts
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

    fun meets(c: Context): MeetOpts = p(c).let {
        MeetOpts(
            it.getBoolean("meet", false), it.getString("meetCals", null).orEmpty().split(',').mapNotNull(String::toLongOrNull).toSet(),
            it.getInt("meetVideo", 5), it.getInt("meetPlace", 15), it.getBoolean("meetOnly", false),
        )
    }

    fun setMeets(c: Context, m: MeetOpts) {
        p(c).edit()
            .putBoolean("meet", m.on).putString("meetCals", m.cals.joinToString(",")).putInt("meetVideo", m.video).putInt("meetPlace", m.place).putBoolean("meetOnly", m.only)
            .apply()
    }

    fun brief(c: Context): BriefOpts = p(c).let { BriefOpts(it.getBoolean("brief", true), it.getInt("briefAt", 8 * 60)) }

    fun setBrief(c: Context, b: BriefOpts) {
        p(c).edit().putBoolean("brief", b.on).putInt("briefAt", b.at).apply()
    }

    fun mirror(c: Context) = p(c).getBoolean("mirror", false)

    fun setMirror(c: Context, on: Boolean) {
        p(c).edit().putBoolean("mirror", on).apply()
    }

    fun since(c: Context): Long {
        val s = p(c)
        return s.getLong(SINCE, 0).takeIf { it > 0 } ?: (System.currentTimeMillis() - 60_000L).also { s.edit().putLong(SINCE, it).apply() }
    }

    fun clear(c: Context) {
        p(c).edit().clear().commit()
    }
}
