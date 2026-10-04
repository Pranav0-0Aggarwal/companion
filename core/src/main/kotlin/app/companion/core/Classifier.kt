package app.companion.core

data class Guess(val label: String, val prob: Float)

sealed interface Verdict {
    val event: Event
    val confidence: Float
    val guess: Guess?
    val tags: List<String>
    val cat: Guess?

    data class Sure(
        override val event: Event,
        override val confidence: Float,
        override val guess: Guess? = null,
        override val tags: List<String> = emptyList(),
        override val cat: Guess? = null,
    ) : Verdict

    data class Unsure(
        override val event: Event,
        override val confidence: Float,
        override val guess: Guess? = null,
        override val tags: List<String> = emptyList(),
        override val cat: Guess? = null,
    ) : Verdict
}

fun interface Classifier {
    fun classify(raw: Raw): Verdict
}
