package app.companion.core

object Weights {
    private const val EXT = ".xnn"

    fun bert(task: String, sha: String) = "bert-$task-${sha.take(8)}$EXT"

    fun decide(sha: String) = "decide-${sha.take(8)}$EXT"

    fun stale(files: Collection<String>, keep: Set<String>) = files.filter { it.endsWith(EXT) && it !in keep }
}
