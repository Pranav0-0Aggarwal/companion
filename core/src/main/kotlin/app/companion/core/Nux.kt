package app.companion.core

object Nux {
    const val MAX_TEXT = 600
    const val MAX_TOKENS = 160
    val GRAMMAR = """
root ::= "{" ws "\"amount\":" ws s "," ws "\"due_date\":" ws s "," ws "\"card_last4\":" ws s ws "}"
s ::= "\"" c{0,40} "\""
c ::= [^"\\\n]
ws ::= [ \t\n]*
""".trimStart()

    val FIELDS = setOf(Field.Amount, Field.Due, Field.Last4)

    private const val TPL = "{\n    \"amount\": \"\",\n    \"due_date\": \"\",\n    \"card_last4\": \"\"\n}"
    private const val CAP = 40
    private val names = mapOf("amount" to Field.Amount, "due_date" to Field.Due, "card_last4" to Field.Last4)
    private val kinds = setOf(Kind.Debit, Kind.Credit, Kind.CardSpend, Kind.Bill, Kind.Statement)
    private val labels = setOf("expense", "income", "bill")

    const val PREFIX = "<|input|>\n### Template:\n$TPL\n### Text:\n"
    const val SUFFIX = "\n\n<|output|>\n"

    fun prompt(text: String) = PREFIX + clip(text) + SUFFIX

    fun keep(want: Set<Field>): Set<Field> = want intersect FIELDS

    fun parse(json: String): Map<Field, String> {
        val m = runCatching { Json.obj(json.trim()) }.getOrNull() ?: return emptyMap()
        return buildMap {
            for ((k, f) in names) (m[k] as? String)?.trim()?.takeIf { it.isNotEmpty() && it.length <= CAP }?.let { put(f, it) }
        }
    }

    fun money(v: Verdict, cal: Calibration): Boolean = when (v) {
        is Verdict.Sure -> v.event.kind in kinds
        is Verdict.Unsure -> v.guess?.let { it.label in labels && cal.sure(Calibration.TYPE, it.label, it.prob) } == true
    }

    fun clip(text: String) = text.take(MAX_TEXT).let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }
}
