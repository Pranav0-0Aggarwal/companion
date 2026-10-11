package app.companion.core

object ConvKey {
    fun of(pkg: String, shortcutId: String?, convTitle: String?, title: String) = "$pkg|${shortcutId ?: convTitle ?: title}"
}
