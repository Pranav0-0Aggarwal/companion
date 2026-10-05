package app.companion.ingest

import android.content.Context
import app.companion.ai.Pending
import app.companion.core.Classifier
import app.companion.core.Gaps
import app.companion.core.Raw
import app.companion.data.Repo
import app.companion.system.Live
import kotlin.coroutines.cancellation.CancellationException

class Ingest(private val app: Context, private val repo: Repo, private val rules: Classifier, private val pending: Pending, private val hooks: Hooks) {
    private val seen = LinkedHashSet<String>()

    private fun first(key: String) = synchronized(seen) {
        seen.add(key).also { if (seen.size > MAX) seen.remove(seen.first()) }
    }

    suspend fun handle(raw: Raw, key: String? = null, refresh: Boolean = true) {
        if (key != null && !first(key)) return
        val p = repo.profileNow()
        if (!p.on(raw.source)) return
        val v = rules.classify(raw)
        val added = repo.add(raw, v, p, true) ?: return
        if (refresh && (added.fresh || added.item.kind == "Otp")) Live.refresh(app)
        runCatching { hooks.after(raw, added) }.onFailure { if (it is CancellationException) throw it }
        if (added.fresh && Gaps.needs(raw, v)) pending.submit(added.item.id, raw, added.item.state)
    }

    private companion object {
        const val MAX = 300
    }
}
