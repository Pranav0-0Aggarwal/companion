package app.companion.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BertTest {
    private fun res(n: String) = String(ClassLoader.getSystemResourceAsStream("bert/$n")!!.readBytes(), Charsets.UTF_8)

    private val bpe = Bpe.fromJson(res("tokenizer.json"))
    private val vectors = (Json.obj(res("vectors.json"))["vectors"] as List<*>).map { it as Map<*, *> }

    private val types = listOf("otp", "expense", "income", "bill", "delivery", "alert", "personal", "promo", "spam")
    private val cats = listOf("food", "groceries", "shopping", "transport", "travel", "bills", "entertainment", "health", "transfer", "other")

    private fun q(l: List<String>) = l.joinToString(",", "[", "]") { "\"$it\"" }

    private fun specJson(arch: String = BertSpec.ARCH, template: String = "{text}", extra: String = "") =
        """{"arch":"$arch","model":"modernbert-large-sms-v1","template":"$template","max_len":128,"buckets":[128,32,64],"pad_id":50283,"tokenizer":"tokenizer.json",$extra
        "inputs":["input_ids","attention_mask"],"tasks":{"type":{"file":"type.tflite","labels":${q(types)},"signatures":{"32":"t32","64":"t64","128":"t128"}},
        "category":{"file":"category.tflite","labels":${q(cats)}}}}"""

    private val spec = BertSpec.fromJson(specJson())

    private fun ints(v: Any?) = (v as List<*>).map { (it as Number).toInt() }

    @Test
    fun `spec reads buckets files labels and signatures`() {
        assertEquals(listOf(32, 64, 128), spec.buckets)
        assertEquals(128, spec.maxLen)
        assertEquals(listOf("tokenizer.json", "type.tflite", "category.tflite"), spec.files)
        assertEquals(types, spec.labels("type"))
        assertEquals("t64", spec.signature("type", 64)?.name)
        assertFalse(spec.signature("type", 64)!!.named)
        assertNull(spec.signature("category", 64))
        assertEquals("category.tflite", spec.file("category"))
        assertEquals("modernbert-large-sms-v1", spec.model)
        assertFalse(spec.maskFirst)
        assertTrue(BertSpec.fromJson(specJson().replace("\"input_ids\",\"attention_mask\"", "\"attention_mask\",\"input_ids\"")).maskFirst)
    }

    private fun sig(b: Int, extra: String = "") =
        """"s$b":{"bucket":$b,"inputs":{"input_ids":[1,$b],"attention_mask":[1,$b]},"dtype":"int32","output":"logits","output_shape":[1,9],"output_dtype":"float32"$extra}"""

    private fun realJson(sigs: String = listOf(64, 96, 128).joinToString(",") { sig(it) }, tok: String = """{"json":"tok.json","compact":"x.mbpe","unk_id":50280}""", cal: String = "\"cal.json\"") =
        """{"arch":"modernbert-classifier","version":1,"template":"{sender}: {text}","max_len":128,"buckets":[64,96,128],"pad_id":50283,"cls_id":50281,"sep_id":50282,"truncation":"right",
        "tasks":{"type":{"file":"type.tflite","labels":${q(types)},"signatures":{$sigs}},"category":{"file":"category.tflite","labels":${q(cats)},"signatures":{$sigs}}},
        "calibration":$cal,"sha256":{"a":"b"},"model":"m","expected":{"x":1},"runtime":{"y":2},"provenance":{},"quantization":{},"tokenizer_extra":1,"tokenizer":$tok,"bucket_rule":"r"}"""

    @Test
    fun `spec reads the named signature map and ignores the other blocks`() {
        val s = BertSpec.fromJson(realJson())
        assertEquals(listOf(64, 96, 128), s.buckets)
        assertEquals("tok.json", s.tokenizer)
        assertEquals("cal.json", s.calibration)
        assertEquals(listOf("tok.json", "type.tflite", "category.tflite"), s.files)
        val g = s.signature("category", 96)!!
        assertEquals("s96", g.name)
        assertEquals("input_ids", g.ids)
        assertEquals("attention_mask", g.mask)
        assertEquals("logits", g.out)
        assertTrue(g.named)
        assertNull(s.signature("type", 32))
        assertEquals("m", s.model)
        assertEquals(50283, s.pad)
        assertEquals(50281, s.cls)
        assertEquals(50282, s.sep)
    }

    @Test
    fun `spec finds the mask input by name in any order`() {
        val s = BertSpec.fromJson(realJson(sigs = sig(64).replace(""""input_ids":[1,64],"attention_mask":[1,64]""", """"attention_mask":[1,64],"input_ids":[1,64]""")))
        val g = s.signature("type", 64)!!
        assertEquals("input_ids", g.ids)
        assertEquals("attention_mask", g.mask)
    }

    @Test
    fun `tokenizer and calibration names default and plain string names still work`() {
        val s = BertSpec.fromJson(realJson(tok = "\"t.json\"", cal = "null"))
        assertEquals("t.json", s.tokenizer)
        assertEquals("calibration.json", s.calibration)
        val d = BertSpec.fromJson(realJson(tok = "{}"))
        assertEquals("tokenizer.json", d.tokenizer)
    }

    @Test
    fun `a signature with a foreign bucket dtype or file name is rejected`() {
        assertFailsWith<Exception> { BertSpec.fromJson(realJson(sigs = sig(32))) }
        assertFailsWith<Exception> { BertSpec.fromJson(realJson(sigs = sig(64).replace("int32", "float32"))) }
        assertFailsWith<Exception> { BertSpec.fromJson(realJson(tok = """{"json":"../tok.json"}""")) }
        assertFailsWith<Exception> { BertSpec.fromJson(realJson(cal = "\"a/b.json\"")) }
        assertFailsWith<Exception> { BertSpec.fromJson(realJson().replace("\"right\"", "\"left\"")) }
    }

    @Test
    fun `the plan lists the named tokenizer and tasks but not the calibration`() {
        val s = BertSpec.fromJson(realJson()).files
        val p = BertPlan.of(realJson(), s.associateWith { "%064x".format(it.length) }) { _, _ -> true }!!
        assertEquals(s.toSet(), p.shas.keys)
    }

    @Test
    fun `spec defaults and rejects other architectures`() {
        val s = BertSpec.fromJson("""{"arch":"modernbert-classifier","buckets":[64],"tasks":{"type":{"file":"t","labels":["a"]}}}""")
        assertEquals("{sender}: {text}", s.template)
        assertEquals(64, s.maxLen)
        assertEquals("tokenizer.json", s.tokenizer)
        assertEquals("calibration.json", s.calibration)
        assertEquals(listOf("input_ids", "attention_mask"), s.inputs)
        assertNull(s.pad)
        assertFailsWith<Exception> { BertSpec.fromJson(specJson(arch = "gliner")) }
        assertFailsWith<Exception> { BertSpec.fromJson("""{"arch":"modernbert-classifier","buckets":[64],"tasks":{"category":{"file":"c","labels":["a"]}}}""") }
        assertFailsWith<Exception> { BertSpec.fromJson("""{"arch":"modernbert-classifier","buckets":[],"tasks":{"type":{"file":"t","labels":["a"]}}}""") }
    }

    @Test
    fun `input equals the reference encoding with cls and sep padded to a bucket`() {
        for (v in vectors) {
            val want = ints(v["enc"])
            val x = Bert.build(spec, bpe, "", v["text"] as String)
            assertEquals(want, x.ids.take(want.size), "ids for ${v["text"]}")
            assertTrue(x.ids.drop(want.size).all { it == 50283 })
            assertEquals(listOf(32, 64, 128).first { it >= want.size }, x.bucket)
            assertEquals(want.size, x.mask.sum())
            assertTrue(x.mask.take(want.size).all { it == 1 })
            assertEquals(50281, x.ids[0])
            assertEquals(50282, x.ids[want.size - 1])
        }
    }

    @Test
    fun `input equals the reference for the 200 synthetic vectors of the real spec`() {
        val real = BertSpec.fromJson(realJson())
        val all = (Json.obj(res("vectors_v2.json"))["vectors"] as List<*>).map { it as Map<*, *> }
        assertEquals(200, all.size)
        for (v in all) {
            val want = ints(v["input_ids"])
            val x = Bert.build(real, bpe, v["sender"] as String, v["text"] as String)
            assertEquals(want, x.ids.take(want.size), "ids for ${v["input"]}")
            assertEquals(listOf(64, 96, 128).first { it >= want.size }, x.bucket)
            assertEquals(want.size, x.mask.sum())
            assertTrue(x.mask.take(want.size).all { it == 1 })
            assertTrue(x.ids.drop(want.size).all { it == 50283 })
        }
    }

    @Test
    fun `long text truncates to 128 with cls first and sep last`() {
        val long = vectors.first { ints(it["ids"]).size > 126 }
        val x = Bert.build(spec, bpe, "", long["text"] as String)
        assertEquals(128, x.bucket)
        assertEquals(50281, x.ids.first())
        assertEquals(50282, x.ids.last())
        assertEquals(ints(long["ids"]).take(126), x.ids.slice(1..126))
        assertTrue(x.mask.all { it == 1 })
    }

    @Test
    fun `bucket is the smallest that fits`() {
        fun bucket(words: Int) = Bert.build(spec, bpe, "", List(words) { "ab" }.joinToString(" ")).bucket
        assertEquals(32, bucket(1))
        assertEquals(32, bucket(30))
        assertEquals(64, bucket(31))
        assertEquals(64, bucket(62))
        assertEquals(128, bucket(63))
        assertEquals(128, bucket(500))
    }

    @Test
    fun `a smaller max length than the largest bucket still caps the input`() {
        val s = BertSpec.fromJson(specJson().replace("\"max_len\":128", "\"max_len\":40"))
        val x = Bert.build(s, bpe, "", "ab ".repeat(200))
        assertEquals(64, x.bucket)
        assertEquals(40, x.mask.sum())
        assertEquals(50282, x.ids[39])
    }

    @Test
    fun `special ids fall back to the tokenizer file`() {
        val s = BertSpec.fromJson("""{"arch":"modernbert-classifier","buckets":[32],"template":"{text}","tasks":{"type":{"file":"t","labels":["a"]}}}""")
        val x = Bert.build(s, bpe, "", "hi")
        assertContentEquals(intArrayOf(50281) + bpe.encode("hi") + intArrayOf(50282), x.ids.take(3).toIntArray())
        assertEquals(50283, x.ids[3])
    }

    @Test
    fun `template joins sender and text once`() {
        val s = BertSpec.fromJson(specJson(template = "{sender}: {text}"))
        assertEquals("VM-HDFCBK: Rs.5 debited", Bert.message(s, "VM-HDFCBK", "Rs.5 debited"))
        assertEquals("{text}: x {sender}", Bert.message(s, "{text}", "x {sender}"))
        val x = Bert.build(s, bpe, "VM-HDFCBK", "Rs.5 debited")
        assertEquals(bpe.encode("VM-HDFCBK: Rs.5 debited").toList(), x.ids.slice(1 until x.mask.sum() - 1))
    }

    private val files = listOf("tokenizer.json", "type.tflite", "category.tflite")
    private val listed = files.associateWith { "%064x".format(it.length) } + ("model_spec.json" to "f".repeat(64))

    @Test
    fun `plan needs every file listed and verified`() {
        val p = assertNotNull(BertPlan.of(specJson(), listed) { _, _ -> true })
        assertEquals(files.toSet(), p.shas.keys)
        assertNull(BertPlan.of(specJson(), listed - "category.tflite") { _, _ -> true })
        assertNull(BertPlan.of(specJson(), listed) { f, _ -> f != "type.tflite" })
        assertNull(BertPlan.of(specJson(), listed) { _, _ -> false })
        assertNull(BertPlan.of(specJson(), emptyMap()) { _, _ -> true })
    }

    @Test
    fun `plan is absent for a missing wrong or broken spec`() {
        assertNull(BertPlan.of(null, listed) { _, _ -> true })
        assertNull(BertPlan.of(specJson(arch = "gliner"), listed) { _, _ -> true })
        assertNull(BertPlan.of("{", listed) { _, _ -> true })
        assertNull(BertPlan.of("""{"arch":"modernbert-classifier"}""", listed) { _, _ -> true })
    }

    @Test
    fun `plan hands each file its listed hash to the verifier`() {
        val seen = HashMap<String, String>()
        BertPlan.of(specJson(), listed) { f, s -> seen.put(f, s).let { true } }
        assertEquals(files.associateWith { listed.getValue(it) }, seen)
    }

    @Test
    fun `key changes with the model files and the calibration`() {
        val p = BertPlan.of(specJson(), listed) { _, _ -> true }!!
        assertEquals(64, p.key(null).length)
        assertEquals(p.key("a"), p.key("a"))
        assertNotEquals(p.key("a"), p.key("b"))
        assertNotEquals(p.key("a"), p.key(null))
        val q = BertPlan.of(specJson(), listed + ("type.tflite" to "1".repeat(64))) { _, _ -> true }!!
        assertNotEquals(p.key("a"), q.key("a"))
        val t = BertPlan.of(specJson(), listed + ("tokenizer.json" to "2".repeat(64))) { _, _ -> true }!!
        assertNotEquals(p.key("a"), t.key("a"))
    }

    private val cal = Calibration.fromJson(
        """{"version":2,"model":"modernbert-large-sms-v1","tasks":{"type":{"temperature":2.0,"labels":${q(types)},"sure":{"promo":0.8,"alert":0.9,"expense":0.95}},"category":{"temperature":0.5,"labels":${q(cats)},"sure":{"food":0.9}}}}""",
    )

    private fun near(a: Float, b: Float) = abs(a - b) < 1e-5f

    private fun logits(top: Int, n: Int, v: Float = 6f) = FloatArray(n) { if (it == top) v else 0f }

    @Test
    fun `the category model is skipped while no category can ever be sure`() {
        val never = Calibration.fromJson(
            """{"version":2,"model":"m","tasks":{"type":{"temperature":1,"labels":${q(types)}},"category":{"temperature":1,"labels":${q(cats)},"sure":${cats.joinToString(",", "{", "}") { "\"$it\":1.01" }}}}}""",
        )
        assertFalse(never.reachable("category"))
        assertTrue(cal.reachable("category"))
        val asked = mutableListOf<String>()
        val s = Bert.scoreAll(spec, never, bpe, listOf("VM-SHOP" to "Rs 99 spent")) { task, xs ->
            asked += task
            xs.map { logits(if (task == "type") types.indexOf("expense") else 0, if (task == "type") types.size else cats.size) }
        }
        assertEquals(listOf("type"), asked)
        assertNull(s.single()!!.category)
    }

    @Test
    fun `version 2 calibration keeps its shape and names the model`() {
        assertEquals("modernbert-large-sms-v1", cal.model)
        assertNull(Calibration.DEFAULT.model)
        assertNull(Calibration.fromJson("""{"version":1,"tasks":{}}""").model)
        assertTrue(cal.fits("type", types))
        assertTrue(cal.fits("category", cats))
        assertEquals(0.8f, cal.bar("type", "promo"))
        assertEquals(0.97f, cal.bar("type", "otp"))
        assertFailsWith<Exception> { Calibration.fromJson("""{"version":3,"tasks":{}}""") }
    }

    @Test
    fun `probabilities are softmax of logits over temperature`() {
        val l = floatArrayOf(1f, 4f, 0f, 0f, 0f, 0f, 0f, 0f, 2f)
        val p = cal.probs("type", l)
        val e = l.map { Math.exp((it / 2f).toDouble()) }
        val want = e.map { (it / e.sum()).toFloat() }
        p.indices.forEach { assertTrue(near(p[it], want[it]), "label $it") }
        assertTrue(near(p.sum(), 1f))
        assertTrue(p[1] < Decide.softmax(l)[1])
        val c = cal.probs("category", floatArrayOf(0f, 1f))
        assertTrue(c[1] > Decide.softmax(floatArrayOf(0f, 1f))[1])
    }

    @Test
    fun `type runs alone unless it is expense and category only then`() {
        for ((i, name) in types.withIndex()) {
            val ran = ArrayList<String>()
            val s = Bert.score(spec, cal, bpe, "VM-X", "hello") { task, _ ->
                ran.add(task)
                if (task == "type") logits(i, types.size) else logits(0, cats.size)
            }!!
            assertEquals(types.toSet(), s.type.keys)
            if (name == "expense") {
                assertEquals(listOf("type", "category"), ran)
                assertEquals(cats.toSet(), s.category!!.keys)
            } else {
                assertEquals(listOf("type"), ran)
                assertNull(s.category, name)
            }
        }
    }

    @Test
    fun `a batch runs every type first then category only for the expense items`() {
        val tops = listOf(1, 5, 1, 0, 1, 8)
        val ran = ArrayList<Pair<String, Int>>()
        val out = Bert.scoreAll(spec, cal, bpe, tops.map { "VM-X" to "hello $it" }) { task, xs ->
            ran.add(task to xs.size)
            if (task == "type") tops.map { logits(it, types.size) } else xs.map { logits(2, cats.size) }
        }
        assertEquals(listOf("type" to 6, "category" to 3), ran)
        assertEquals(listOf(true, false, true, false, true, false), out.map { it!!.category != null })
        assertEquals(tops, out.map { s -> s!!.type.maxBy { it.value }.key }.map(types::indexOf))
    }

    @Test
    fun `a batch with no expense never loads the category task and an empty batch runs nothing`() {
        val ran = ArrayList<String>()
        val out = Bert.scoreAll(spec, cal, bpe, listOf("a" to "b", "c" to "d")) { task, xs -> ran.add(task).let { xs.map { logits(5, types.size) } } }
        assertEquals(listOf("type"), ran)
        assertTrue(out.all { it != null && it.category == null })
        assertEquals(emptyList(), Bert.scoreAll(spec, cal, bpe, emptyList()) { t, _ -> ran.add(t).let { emptyList() } })
        assertEquals(listOf("type"), ran)
    }

    @Test
    fun `a batch keeps a failed item null and its neighbours scored`() {
        val out = Bert.scoreAll(spec, cal, bpe, listOf("a" to "1", "a" to "2", "a" to "3")) { task, xs ->
            if (task == "type") listOf(logits(1, types.size), null, FloatArray(3)) else xs.map { null }
        }
        assertNotNull(out[0])
        assertNull(out[0]!!.category)
        assertNull(out[1])
        assertNull(out[2])
    }

    @Test
    fun `a batch of one matches the single score`() {
        fun l(task: String) = if (task == "type") logits(1, types.size) else logits(3, cats.size)
        val one = Bert.score(spec, cal, bpe, "VM-X", "hello") { t, _ -> l(t) }!!
        val all = Bert.scoreAll(spec, cal, bpe, listOf("VM-X" to "hello")) { t, xs -> xs.map { l(t) } }.single()!!
        assertEquals(one.type, all.type)
        assertEquals(one.category, all.category)
    }

    @Test
    fun `calibrated probabilities reach the scored maps`() {
        val s = Bert.score(spec, cal, bpe, "VM-X", "hello") { task, _ -> if (task == "type") logits(1, types.size) else logits(0, cats.size) }!!
        assertTrue(near(s.type.getValue("expense"), cal.probs("type", logits(1, types.size))[1]))
        assertTrue(near(s.category!!.getValue("food"), cal.probs("category", logits(0, cats.size))[0]))
        assertTrue(near(s.type.values.sum(), 1f))
    }

    @Test
    fun `both tasks see the same input and their own signature`() {
        val seen = ArrayList<Pair<String, DecideInput>>()
        Bert.score(spec, cal, bpe, "VM-X", "hello") { task, x ->
            seen.add(task to x)
            if (task == "type") logits(1, types.size) else logits(0, cats.size)
        }
        assertEquals(2, seen.size)
        assertContentEquals(seen[0].second.ids, seen[1].second.ids)
        assertEquals("t32", spec.signature("type", seen[0].second.bucket)?.name)
    }

    @Test
    fun `a failed or mis sized run gives no score and a failed category gives no category`() {
        assertNull(Bert.score(spec, cal, bpe, "a", "b") { _, _ -> null })
        assertNull(Bert.score(spec, cal, bpe, "a", "b") { _, _ -> FloatArray(3) })
        val s = Bert.score(spec, cal, bpe, "a", "b") { task, _ -> if (task == "type") logits(1, types.size) else null }!!
        assertNull(s.category)
    }

    @Test
    fun `a spec without a category task never runs one`() {
        val s = BertSpec.fromJson("""{"arch":"modernbert-classifier","buckets":[32],"tasks":{"type":{"file":"t","labels":${q(types)}}}}""")
        val ran = ArrayList<String>()
        val r = Bert.score(s, cal, bpe, "a", "b") { task, _ -> ran.add(task).let { logits(1, types.size) } }!!
        assertEquals(listOf("type"), ran)
        assertNull(r.category)
    }

    private val rules = RulesClassifier(IST)
    private val unsure = sms("VM-ACMEBK-S", "Your plan renewal summary")

    private fun verdict(top: Int, v: Float) =
        DecideClassifier(rules, { cal }) { Bert.score(spec, cal, bpe, it.sender, it.body) { _, _ -> logits(top, types.size, v) } }.classify(unsure)

    @Test
    fun `only a probability at or above the label bar is sure`() {
        val alert = types.indexOf("alert")
        val hi = assertIs<Verdict.Sure>(verdict(alert, 12f))
        assertEquals("alert", hi.guess!!.label)
        val lo = assertIs<Verdict.Unsure>(verdict(alert, 3.5f))
        assertEquals("alert", lo.guess!!.label)
        assertTrue(lo.confidence < 0.9f)
    }

    @Test
    fun `a label bar above one is never sure`() {
        val never = Calibration.fromJson("""{"version":2,"model":"m","tasks":{"type":{"temperature":1,"labels":${q(types)},"sure":{"alert":1.01}}}}""")
        val c = DecideClassifier(rules, { never }) { Bert.score(spec, never, bpe, it.sender, it.body) { _, _ -> logits(types.indexOf("alert"), types.size, 30f) } }
        assertIs<Verdict.Unsure>(c.classify(unsure))
    }
}
