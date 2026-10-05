package app.companion.core

import java.util.Locale

class DecideSpec(
    val pad: Int,
    val buckets: List<Int>,
    private val prefix: Map<String, IntArray>,
    private val labels: Map<String, List<String>>,
    private val signatures: Map<String, Map<Int, String>> = emptyMap(),
    val template: String = TEMPLATE,
    val splitter: Regex = SPLITTER,
) {
    fun prefix(task: String) = prefix.getValue(task)

    fun labels(task: String) = labels.getValue(task)

    fun signature(task: String, bucket: Int) = signatures[task]?.get(bucket)

    fun has(task: String) = task in labels

    companion object {
        const val TEMPLATE = "Text message from {sender}:\n{body}"
        val SPLITTER = Regex(
            "(?:https?://[^\\t\\n\\x0B\\f\\r\\x1C-\\x1F \\x85\\xA0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]+|www\\.[^\\t\\n\\x0B\\f\\r\\x1C-\\x1F \\x85\\xA0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]+)" +
                "|[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}|@[a-z0-9_]+|[\\p{L}\\p{N}_]+(?:[-_][\\p{L}\\p{N}_]+)*" +
                "|[^\\t\\n\\x0B\\f\\r\\x1C-\\x1F \\x85\\xA0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]",
            RegexOption.IGNORE_CASE,
        )

        private fun ints(v: Any?) = (v as? List<*>)?.map { (it as Number).toInt() }?.toIntArray() ?: IntArray(0)

        @Suppress("UNCHECKED_CAST")
        fun fromJson(text: String): DecideSpec {
            val m = Json.obj(text)
            val tasks = m["tasks"] as Map<String, Map<String, Any?>>
            return DecideSpec(
                pad = (m["pad_id"] as? Number)?.toInt() ?: 0,
                buckets = (m["buckets"] as? List<*>)?.map { (it as Number).toInt() } ?: listOf(128, 256),
                prefix = tasks.mapValues { ints(it.value["prefix"]) },
                labels = tasks.mapValues { (it.value["labels"] as List<*>).map { l -> l as String } },
                signatures = tasks.mapValues { (it.value["signatures"] as? Map<*, *>)?.entries?.associate { (b, k) -> (b as String).toInt() to k as String } ?: emptyMap() },
                template = m["template"] as? String ?: TEMPLATE,
                splitter = (m["splitter_kotlin"] as? String)?.let { Regex(it, RegexOption.IGNORE_CASE) } ?: SPLITTER,
            )
        }
    }
}

class DecideInput(val ids: IntArray, val mask: IntArray) {
    val bucket get() = ids.size
}

object Decide {
    fun words(spec: DecideSpec, text: String): List<String> {
        val t = if (text.endsWith(".") || text.endsWith("!") || text.endsWith("?")) text else "$text."
        return spec.splitter.findAll(t).map { it.value.lowercase(Locale.ROOT) }.toList()
    }

    fun ids(spec: DecideSpec, text: String, tok: SpTokenizer): IntArray = words(spec, text).flatMap { tok.word(it).toList() }.toIntArray()

    fun message(spec: DecideSpec, sender: String, body: String) =
        Regex("\\{sender\\}|\\{body\\}").replace(spec.template) { if (it.value == "{sender}") sender else body }

    fun build(spec: DecideSpec, task: String, sender: String, body: String, tok: SpTokenizer): DecideInput {
        val cap = spec.buckets.max()
        val x = (spec.prefix(task) + ids(spec, message(spec, sender, body), tok)).let { it.copyOf(minOf(it.size, cap)) }
        val size = spec.buckets.sorted().firstOrNull { it >= x.size } ?: cap
        val ids = IntArray(size) { spec.pad }
        x.copyInto(ids)
        return DecideInput(ids, IntArray(size) { if (it < x.size) 1 else 0 })
    }

    fun softmax(x: FloatArray): FloatArray {
        val m = x.max()
        val e = FloatArray(x.size) { kotlin.math.exp(x[it] - m) }
        val s = e.sum()
        return FloatArray(x.size) { e[it] / s }
    }

    fun top(spec: DecideSpec, task: String, logits: FloatArray): Pair<String, Float> {
        val p = softmax(logits)
        val i = p.indices.maxBy { p[it] }
        return spec.labels(task)[i] to p[i]
    }
}

class DecideClassifier(
    private val rules: Classifier,
    private val cal: () -> Calibration = { Calibration.DEFAULT },
    private val scorer: Scorer,
) : Classifier {
    override fun classify(raw: Raw): Verdict {
        val v = rules.classify(raw)
        if (v is Verdict.Sure) return v
        val s = scorer.score(raw) ?: return v
        val c = cal()
        val p = c.pick(Calibration.TYPE, s.type) ?: return v
        val guess = Guess(p.label, p.prob)
        val cat = s.category?.let { c.pick(Calibration.CATEGORY, it) }?.takeIf { it.sure }?.let { Guess(it.label, it.prob) }
        val e = Labels.event(p.label, v.event, raw)
        return if (p.sure && e != null) {
            Verdict.Sure(e, p.prob, guess, p.tags, cat)
        } else {
            Verdict.Unsure(v.event, maxOf(v.confidence, p.prob.coerceAtMost(CAP)), guess, p.tags, cat)
        }
    }

    private companion object {
        const val CAP = 0.89f
    }
}
