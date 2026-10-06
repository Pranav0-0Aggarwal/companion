package app.companion.ingest

import android.content.Context
import app.companion.ai.Pending
import app.companion.core.Chats
import app.companion.core.Classifier
import app.companion.core.Gaps
import app.companion.core.Raw
import app.companion.core.Source
import app.companion.data.Repo
import app.companion.data.State
import app.companion.system.Live
import kotlin.coroutines.cancellation.CancellationException

class Ingest(private val app: Context, private val repo: Repo, private val rules: Classifier, private val pending: Pending, private val hooks: Hooks) {
    private val seen = LinkedHashSet<String>()

    private fun first(key: String) = synchronized(seen) {
        seen.add(key).also { if (seen.size > MAX) seen.remove(seen.first()) }
    }

    suspend fun handle(raw: Raw, key: String? = null, refresh: Boolean = true, live: Boolean = true) {
        if (key != null && !first(key)) return
        val p = repo.profileNow()
        if (!p.on(raw.source) || raw.source == Source.Notif && raw.sender == Allow.GMAIL && p.mail) return
        val r = Chats.route(raw, p.vips)
        val v = rules.classify(r)
        val added = repo.add(r, v, p, live) ?: return
        if (added.item.state == State.LOW) return
        if (refresh && (added.fresh || added.item.kind == "Otp")) Live.refresh(app)
        runCatching { hooks.after(r, added) }.onFailure { if (it is CancellationException) throw it }
        if (added.fresh && Gaps.needs(r, v)) pending.submit(added.item.id, r, added.item.state)
    }

    private companion object {
        const val MAX = 300
    }
}
