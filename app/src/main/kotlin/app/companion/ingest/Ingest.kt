package app.companion.ingest

import android.content.Context
import app.companion.core.Classifier
import app.companion.core.Raw
import app.companion.data.Repo
import app.companion.system.Live

class Ingest(private val app: Context, private val repo: Repo, private val clf: Classifier) {
    private val seen = LinkedHashSet<String>()

    private fun first(key: String) = synchronized(seen) {
        seen.add(key).also { if (seen.size > MAX) seen.remove(seen.first()) }
    }

    suspend fun handle(raw: Raw, key: String? = null, refresh: Boolean = true) {
        if (key != null && !first(key)) return
        val p = repo.profileNow()
        if (!p.on(raw.source)) return
        val added = repo.add(raw, clf.classify(raw), p) ?: return
        if (refresh && (added.fresh || added.item.kind == "Otp")) Live.refresh(app)
    }

    private companion object {
        const val MAX = 300
    }
}
