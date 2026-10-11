package app.companion.link

import kotlinx.coroutines.CoroutineScope

interface LinkFeature {
    val key: String
    val cmds: Set<String>
    suspend fun run(t: String, q: Obj): Obj = emptyMap()
    fun start(sc: CoroutineScope, out: (Obj) -> Unit) {}
    fun stop() {}
}

object LinkFeatures {
    val all = mutableListOf<LinkFeature>()
    fun of(t: String) = all.firstOrNull { t in it.cmds }
}
