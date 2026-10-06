package app.companion.core

import kotlin.math.abs

data class Sig(val fp: Fingerprint, val at: Long, val src: String, val from: String?, val ref: String?)

object Dupes {
    const val SPAN = 7L * 24 * 60 * 60 * 1000

    private val refRx = Regex("(?i)\\b(?:upi|utr|rrn|imps|neft|ref(?:erence)?|txn|transaction)(?:\\s*(?:ref(?:erence)?|id|no\\.?|number))?\\s*[:#./-]?\\s*(?<!\\d)(\\d{12})(?!\\d)")

    fun ref(text: String?) = text?.let { refRx.find(it)?.groupValues?.get(1) }

    fun same(a: Sig, b: Sig): Boolean {
        val x = a.fp
        val y = b.fp
        if (x.group != y.group) return false
        if (x.group == Group.Code || x.group == Group.Due) return Fingerprint.same(x, a.at, y, b.at)
        if (x.paise != y.paise || abs(a.at - b.at) > SPAN) return false
        if (a.ref != null && b.ref != null) return a.ref == b.ref
        return Fingerprint.same(x, a.at, y, b.at) || Fingerprint.echo(x, a.at, a.src, a.from, y, b.at, b.src, b.from)
    }
}
