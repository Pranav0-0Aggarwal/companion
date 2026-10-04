package app.companion.core

object Corrections {
    fun text(title: String, note: String) = listOf(title, note).filter { it.isNotBlank() }.joinToString("\n")

    fun line(sender: String?, title: String, note: String, task: String, model: String?, prob: Float?, chosen: String) = Json.write(
        linkedMapOf("sender" to sender.orEmpty(), "text" to text(title, note), "task" to task, "model" to model, "modelProb" to prob, "chosen" to chosen),
    )
}
