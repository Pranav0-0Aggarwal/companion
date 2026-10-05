package app.companion.core

import java.security.MessageDigest

class BertSpec(
    val model: String?,
    val template: String,
    val maxLen: Int,
    val buckets: List<Int>,
    val pad: Int?,
    val cls: Int?,
    val sep: Int?,
    val tokenizer: String,
    val calibration: String,
    val inputs: List<String>,
    private val tasks: Map<String, Task>,
) {
    class Sig(val name: String, val ids: String? = null, val mask: String? = null, val out: String? = null) {
        val named get() = ids != null && mask != null && out != null
    }

    class Task(val file: String, val labels: List<String>, val signatures: Map<Int, Sig>)

    val maskFirst get() = inputs.size > 1 && inputs[0].contains("mask", ignoreCase = true)

    val files get() = listOf(tokenizer) + tasks.values.map { it.file }

    fun has(task: String) = task in tasks

    fun keep(ok: (String) -> Boolean) = BertSpec(model, template, maxLen, buckets, pad, cls, sep, tokenizer, calibration, inputs, tasks.filter { (t, v) -> t == Calibration.TYPE || ok(v.file) })

    fun labels(task: String) = tasks.getValue(task).labels

    fun file(task: String) = tasks.getValue(task).file

    fun signature(task: String, bucket: Int) = tasks[task]?.signatures?.get(bucket)

    companion object {
        const val ARCH = "modernbert-classifier"
        const val TEMPLATE = "{sender}: {text}"
        const val CAL = "calibration.json"

        private fun plain(raw: String) = raw.isNotEmpty() && raw.length <= 128 && ".." !in raw && raw.none { it == '/' || it == '\\' || it < ' ' }

        private fun name(v: Any?, key: String? = null): String? =
            (if (key != null && v is Map<*, *>) v[key] else v) as? String

        private fun sigs(raw: Any?, buckets: List<Int>): Map<Int, Sig> = (raw as? Map<*, *>)?.entries?.associate { (k, v) ->
            val key = k as String
            if (v is Map<*, *>) {
                val b = (v["bucket"] as Number).toInt()
                require(b in buckets && (v["dtype"] ?: "int32") == "int32")
                val ins = (v["inputs"] as? Map<*, *>)?.keys?.map { it as String }.orEmpty()
                val mask = ins.firstOrNull { it.contains("mask", ignoreCase = true) }
                val ids = ins.firstOrNull { it != mask }
                b to if (ids != null && mask != null) Sig(key, ids, mask, v["output"] as? String ?: "logits") else Sig(key)
            } else {
                key.toInt() to Sig(v as String)
            }
        } ?: emptyMap()

        @Suppress("UNCHECKED_CAST")
        fun fromJson(text: String): BertSpec {
            val m = Json.obj(text)
            require(m["arch"] == ARCH)
            val buckets = (m["buckets"] as List<*>).map { (it as Number).toInt() }.sorted()
            require(buckets.isNotEmpty() && buckets.all { it >= 3 } && (m["truncation"] ?: "right") == "right")
            val tasks = (m["tasks"] as Map<String, Map<String, Any?>>).mapValues { (_, t) ->
                val labels = (t["labels"] as List<*>).map { it as String }
                require(labels.isNotEmpty())
                Task(t["file"] as String, labels, sigs(t["signatures"], buckets))
            }
            require(Calibration.TYPE in tasks)
            fun id(k: String) = (m[k] as? Number)?.toInt()
            val spec = BertSpec(
                model = m["model"] as? String,
                template = m["template"] as? String ?: TEMPLATE,
                maxLen = (m["max_len"] as? Number)?.toInt() ?: buckets.last(),
                buckets = buckets,
                pad = id("pad_id"),
                cls = id("cls_id"),
                sep = id("sep_id"),
                tokenizer = name(m["tokenizer"], "json") ?: "tokenizer.json",
                calibration = name(m["calibration"], "file") ?: CAL,
                inputs = (m["inputs"] as? List<*>)?.map { it as String } ?: listOf("input_ids", "attention_mask"),
                tasks = tasks,
            )
            require((spec.files + spec.calibration).all { plain(it) })
            return spec
        }
    }
}

class BertPlan(val spec: BertSpec, val shas: Map<String, String>) {
    fun key(cal: String?): String {
        val s = "bert\n" + shas.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" } + "\n" + (cal ?: "")
        return MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun of(spec: String?, listed: Map<String, String>, ok: (String, String) -> Boolean): BertPlan? {
            val all = runCatching { BertSpec.fromJson(spec ?: return null) }.getOrNull() ?: return null
            val s = all.keep { f -> listed[f]?.let { ok(f, it) } == true }
            if (!s.has(Calibration.TYPE)) return null
            val shas = s.files.associateWith { f -> listed[f]?.takeIf { ok(f, it) } ?: return null }
            return BertPlan(s, shas)
        }
    }
}

object Bert {
    const val EXPENSE = "expense"

    fun message(spec: BertSpec, sender: String, text: String) =
        Regex("\\{sender\\}|\\{text\\}").replace(spec.template) { if (it.value == "{sender}") sender else text }

    fun build(spec: BertSpec, bpe: Bpe, sender: String, text: String): DecideInput {
        val cap = minOf(spec.maxLen, spec.buckets.last())
        val body = bpe.encode(message(spec, sender, text), cap - 2)
        val cls = spec.cls ?: checkNotNull(bpe.id("[CLS]"))
        val sep = spec.sep ?: checkNotNull(bpe.id("[SEP]"))
        val pad = spec.pad ?: checkNotNull(bpe.id("[PAD]"))
        val n = body.size + 2
        val size = spec.buckets.first { it >= n }
        val ids = IntArray(size) { pad }
        ids[0] = cls
        body.copyInto(ids, 1)
        ids[n - 1] = sep
        return DecideInput(ids, IntArray(size) { if (it < n) 1 else 0 })
    }

    fun score(spec: BertSpec, cal: Calibration, bpe: Bpe, sender: String, text: String, logits: (String, DecideInput) -> FloatArray?): Scored? =
        scoreAll(spec, cal, bpe, listOf(sender to text)) { task, xs -> xs.map { logits(task, it) } }.single()

    fun scoreAll(
        spec: BertSpec,
        cal: Calibration,
        bpe: Bpe,
        items: List<Pair<String, String>>,
        logits: (String, List<DecideInput>) -> List<FloatArray?>,
    ): List<Scored?> {
        val xs = items.map { (sender, text) -> build(spec, bpe, sender, text) }

        fun probs(task: String, at: List<Int>): Map<Int, Map<String, Float>> {
            if (at.isEmpty()) return emptyMap()
            val labels = spec.labels(task)
            val out = logits(task, at.map(xs::get))
            return at.indices.mapNotNull { k ->
                val l = out.getOrNull(k)?.takeIf { it.size == labels.size } ?: return@mapNotNull null
                val p = cal.probs(task, l)
                at[k] to labels.mapIndexed { i, name -> name to p[i] }.toMap()
            }.toMap()
        }

        val type = probs(Calibration.TYPE, xs.indices.toList())
        val more = if (spec.has(Calibration.CATEGORY) && cal.reachable(Calibration.CATEGORY)) type.filterValues { it.maxBy { e -> e.value }.key == EXPENSE }.keys.sorted() else emptyList()
        val cat = probs(Calibration.CATEGORY, more)
        return xs.indices.map { i -> type[i]?.let { Scored(it, cat[i]) } }
    }
}
