package app.companion.core

import kotlin.math.exp

class Calibration(private val tasks: Map<String, Task> = emptyMap()) {
    class Task(val temperature: Float, val labels: List<String>, val sure: Map<String, Float>, val act: String = SOFTMAX)

    class Pick(val label: String, val prob: Float, val sure: Boolean, val tags: List<String>)

    fun act(task: String) = tasks[task]?.act ?: SOFTMAX

    fun probs(task: String, logits: FloatArray): FloatArray {
        val t = tasks[task]?.temperature ?: 1f
        val x = FloatArray(logits.size) { logits[it] / t }
        return if (act(task) == SIGMOID) FloatArray(x.size) { 1f / (1f + exp(-x[it])) } else Decide.softmax(x)
    }

    fun pick(task: String, probs: Map<String, Float>): Pick? {
        val top = probs.maxByOrNull { it.value } ?: return null
        val tags = if (act(task) == SIGMOID) probs.entries.filter { it.key != top.key && it.value >= TAG }.sortedByDescending { it.value }.map { it.key } else emptyList()
        return Pick(top.key, top.value, sure(task, top.key, top.value), tags)
    }

    fun bar(task: String, label: String) = tasks[task]?.sure?.get(label.lowercase()) ?: BAR

    fun sure(task: String, label: String, p: Float) = p >= bar(task, label)

    fun fits(task: String, labels: List<String>) =
        tasks[task]?.labels?.let { it.isEmpty() || it == labels.map(String::lowercase) } ?: true

    companion object {
        const val TYPE = "type"
        const val CATEGORY = "category"
        const val SOFTMAX = "softmax"
        const val SIGMOID = "sigmoid"
        const val BAR = 0.97f
        const val TAG = 0.5f
        val DEFAULT = Calibration()

        fun fromJson(text: String): Calibration {
            val m = Json.obj(text)
            require((m["version"] as? Number)?.toInt() == 1)
            val tasks = (m["tasks"] as Map<*, *>).entries.associate { (k, v) ->
                val t = v as Map<*, *>
                val temp = (t["temperature"] as Number).toFloat()
                require(temp.isFinite() && temp > 0f)
                val labels = (t["labels"] as? List<*>)?.map { (it as String).lowercase() } ?: emptyList()
                val sure = (t["sure"] as? Map<*, *>)?.entries?.associate { (l, b) ->
                    val bar = (b as Number).toFloat()
                    require(bar > 0f && bar <= 1f)
                    (l as String).lowercase() to bar
                } ?: emptyMap()
                require(labels.isEmpty() || labels.containsAll(sure.keys))
                val act = (t["act"] as? String) ?: SOFTMAX
                require(act == SOFTMAX || act == SIGMOID)
                (k as String) to Task(temp, labels, sure, act)
            }
            return Calibration(tasks)
        }
    }
}
