package app.companion.core

object Twin {
    const val SLACK = 2000L

    fun times(sent: Long, date: Long) = listOf(sent.takeIf { it > 0 } ?: date, date)

    fun spans(sent: Long, date: Long) = times(sent, date).map { (it - SLACK)..(it + SLACK) }

    fun same(sender: String, at: Long, other: String, sent: Long, date: Long) = sender == other && spans(sent, date).any { at in it }
}
