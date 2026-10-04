package app.companion.core

object Corrections {
    fun text(title: String, note: String, body: String? = null) =
        listOf(title, body?.takeIf { it.isNotBlank() } ?: note).filter { it.isNotBlank() }.joinToString("\n")

    fun line(sender: String?, title: String, note: String, body: String?, task: String, model: String?, prob: Float?, chosen: String) = Json.write(
        linkedMapOf("sender" to sender.orEmpty(), "text" to text(title, note, body), "task" to task, "model" to model, "modelProb" to prob, "chosen" to chosen),
    )
}
