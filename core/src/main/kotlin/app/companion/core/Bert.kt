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
    val inputs: List<String>,
    private val tasks: Map<String, Task>,
) {
    class Task(val file: String, val labels: List<String>, val signatures: Map<Int, String>)

    val maskFirst get() = inputs.size > 1 && inputs[0].contains("mask", ignoreCase = true)

    val files get() = listOf(tokenizer) + tasks.values.map { it.file }

    fun has(task: String) = task in tasks

    fun labels(task: String) = tasks.getValue(task).labels

    fun file(task: String) = tasks.getValue(task).file

    fun signature(task: String, bucket: Int) = tasks[task]?.signatures?.get(bucket)

    companion object {
        const val ARCH = "modernbert-classifier"
        const val TEMPLATE = "{sender}: {text}"

        @Suppress("UNCHECKED_CAST")
        fun fromJson(text: String): BertSpec {
            val m = Json.obj(text)
            require(m["arch"] == ARCH)
            val buckets = (m["buckets"] as List<*>).map { (it as Number).toInt() }.sorted()
            require(buckets.isNotEmpty() && buckets.all { it >= 3 })
            val tasks = (m["tasks"] as Map<String, Map<String, Any?>>).mapValues { (_, t) ->
                val labels = (t["labels"] as List<*>).map { it as String }
                require(labels.isNotEmpty())
                val sigs = (t["signatures"] as? Map<*, *>)?.entries?.associate { (b, k) -> (b as String).toInt() to k as String } ?: emptyMap()
                Task(t["file"] as String, labels, sigs)
            }
            require(Calibration.TYPE in tasks)
            fun id(k: String) = (m[k] as? Number)?.toInt()
            return BertSpec(
                model = m["model"] as? String,
                template = m["template"] as? String ?: TEMPLATE,
                maxLen = (m["max_len"] as? Number)?.toInt() ?: buckets.last(),
                buckets = buckets,
                pad = id("pad_id"),
                cls = id("cls_id"),
                sep = id("sep_id"),
                tokenizer = m["tokenizer"] as? String ?: "tokenizer.json",
                inputs = (m["inputs"] as? List<*>)?.map { it as String } ?: listOf("input_ids", "attention_mask"),
                tasks = tasks,
            )
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
            val s = runCatching { BertSpec.fromJson(spec ?: return null) }.getOrNull() ?: return null
            val shas = s.files.associateWith { f -> listed[f]?.takeIf { ok(f, it) } ?: return null }
            return BertPlan(s, shas)
        }
    }
}

object Bert {
    const val EXPENSE = "expense"

    fun message(spec: BertSpec, sender: String, text: String) =
        Regex("\\{sender}|\\{text}").replace(spec.template) { if (it.value == "{sender}") sender else text }

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

    fun score(spec: BertSpec, cal: Calibration, bpe: Bpe, sender: String, text: String, logits: (String, DecideInput) -> FloatArray?): Scored? {
        val x = build(spec, bpe, sender, text)

        fun probs(task: String): Map<String, Float>? {
            val labels = spec.labels(task)
            val l = logits(task, x)?.takeIf { it.size == labels.size } ?: return null
            val p = cal.probs(task, l)
            return labels.mapIndexed { i, name -> name to p[i] }.toMap()
        }

        val type = probs(Calibration.TYPE) ?: return null
        val cat = if (spec.has(Calibration.CATEGORY) && type.maxBy { it.value }.key == EXPENSE) probs(Calibration.CATEGORY) else null
        return Scored(type, cat)
    }
}
