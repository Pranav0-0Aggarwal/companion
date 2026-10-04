package app.companion.core

sealed interface Verdict {
    val event: Event
    val confidence: Float

    data class Sure(override val event: Event, override val confidence: Float) : Verdict
    data class Unsure(override val event: Event, override val confidence: Float) : Verdict
}

fun interface Classifier {
    fun classify(raw: Raw): Verdict
}
