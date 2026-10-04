package app.companion.core

class DecideSpec(
    val pad: Int,
    val buckets: List<Int>,
    private val prefix: Map<String, IntArray>,
    private val suffix: Map<String, IntArray>,
    private val labels: Map<String, List<String>>,
) {
    fun prefix(task: String) = prefix.getValue(task)

    fun suffix(task: String) = suffix[task] ?: IntArray(0)

    fun labels(task: String) = labels.getValue(task)

    companion object {
        private fun ints(v: Any?) = (v as? List<*>)?.map { (it as Number).toInt() }?.toIntArray() ?: IntArray(0)

        @Suppress("UNCHECKED_CAST")
        fun fromJson(text: String): DecideSpec {
            val m = Json.obj(text)
            val tasks = m["tasks"] as Map<String, Map<String, Any?>>
            return DecideSpec(
                pad = (m["pad_id"] as? Number)?.toInt() ?: 0,
                buckets = (m["buckets"] as? List<*>)?.map { (it as Number).toInt() } ?: listOf(128, 256),
                prefix = tasks.mapValues { ints(it.value["prefix"]) },
                suffix = tasks.mapValues { ints(it.value["suffix"]) },
                labels = tasks.mapValues { (it.value["labels"] as List<*>).map { l -> l as String } },
            )
        }
    }
}

class DecideInput(val ids: IntArray, val mask: IntArray) {
    val bucket get() = ids.size
}

object Decide {
    fun build(spec: DecideSpec, task: String, text: String, tok: SpTokenizer): DecideInput {
        val head = spec.prefix(task)
        val tail = spec.suffix(task)
        val cap = spec.buckets.max()
        val body = tok.encode(text).let { it.copyOf(minOf(it.size, (cap - head.size - tail.size).coerceAtLeast(0))) }
        val len = head.size + body.size + tail.size
        val size = spec.buckets.sorted().firstOrNull { it >= len } ?: cap
        val ids = IntArray(size) { spec.pad }
        head.copyInto(ids)
        body.copyInto(ids, head.size)
        tail.copyInto(ids, head.size + body.size)
        return DecideInput(ids, IntArray(size) { if (it < len) 1 else 0 })
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

fun interface Scorer {
    fun score(raw: Raw): Map<Kind, Float>?
}

class DecideClassifier(private val rules: Classifier, private val bar: Float = 0.9f, private val scorer: Scorer) : Classifier {
    override fun classify(raw: Raw): Verdict {
        val v = rules.classify(raw)
        if (v is Verdict.Sure) return v
        val s = scorer.score(raw) ?: return v
        val top = s.maxByOrNull { it.value } ?: return v
        return when {
            top.key == Kind.Promo && top.value >= bar && v.event !is Event.Otp -> Verdict.Sure(Event.Promo, top.value)
            top.key == Kind.Alert && top.value >= bar && (v.event == Event.Alert || v.event == Event.Unknown) -> Verdict.Sure(Event.Alert, top.value)
            top.key == Kind.Unknown && top.value >= bar && (v.event == Event.Unknown || v.event == Event.Alert) -> Verdict.Sure(Event.Unknown, top.value)
            else -> Verdict.Unsure(v.event, maxOf(v.confidence, top.value.coerceAtMost(bar - 0.01f)))
        }
    }
}
