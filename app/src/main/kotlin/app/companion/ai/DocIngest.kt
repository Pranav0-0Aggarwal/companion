package app.companion.ai

import app.companion.core.DocExtract
import app.companion.core.DocFields
import app.companion.core.DocKind
import app.companion.core.DocNux
import app.companion.core.Mask
import app.companion.data.Repo
import java.time.LocalDate
import java.time.ZoneId

class DocIngest(private val repo: Repo, private val nux: NuExtractor) {
    suspend fun text(text: String, src: String, photo: ByteArray? = null, model: Boolean = false): Long? {
        val ref = LocalDate.now(ZoneId.systemDefault())
        val kind = DocExtract.kind(text) ?: if (photo != null) DocKind.Other else return null
        var f = DocExtract.fields(kind, text, ref)
        if (model && (f.number == null || f.expires == null)) {
            nux.doc(kind, text)?.let { json ->
                val m = DocNux.fields(kind, DocNux.parse(kind, json, text), ref)
                f = DocFields(kind, f.title, f.number ?: m.number, f.expires ?: m.expires, m.extra + f.extra)
            }
        }
        if (f.number == null && f.expires == null) return null
        if (repo.docs.has(kind, f.number)) return null
        return repo.docs.add(kind, f.title, f.number, f.expires, f.extra, src, photo)
    }
}
