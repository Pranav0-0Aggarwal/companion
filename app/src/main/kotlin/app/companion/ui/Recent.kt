package app.companion.ui

import android.content.Context

object Recent {
    private const val PREFS = "ask"
    private const val KEY = "recent"
    private const val MAX = 6

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(c: Context): List<String> = prefs(c).getString(KEY, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    fun add(c: Context, q: String) {
        val l = (listOf(q) + list(c).filter { !it.equals(q, true) }).take(MAX)
        prefs(c).edit().putString(KEY, l.joinToString("\n")).apply()
    }

    fun clear(c: Context) {
        prefs(c).edit().clear().commit()
    }
}
