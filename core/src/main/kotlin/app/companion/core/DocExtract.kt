package app.companion.core

import java.time.LocalDate

data class DocFields(val kind: DocKind, val title: String, val number: String?, val expires: LocalDate?, val extra: Map<String, String>)

object DocExtract {
    private val epoch = LocalDate.of(2000, 1, 1)
    private const val STATES = "AN|AP|AR|AS|BR|CG|CH|DD|DL|DN|GA|GJ|HP|HR|JH|JK|KA|KL|LA|LD|MH|ML|MN|MP|MZ|NL|OD|OR|PB|PY|RJ|SK|TN|TR|TS|UK|UP|WB"
    private const val BASE = "expir\\w*|valid(?:ity)?(?:\\s+(?:till|until|upto|up\\s+to|through|thru|to|on))?|ends?|end\\s+date|(?:till|until|upto)"
    private val vehicle = Regex("(?<![A-Za-z0-9])(?:$STATES)[ -]?\\d{1,2}[ -]?[A-Z]{1,3}[ -]?\\d{4}(?![A-Za-z0-9])")
    private val policy = Regex("(?i)polic(?:y|ies)\\s*(?:(?:no|number|num)\\.?|#)?\\s*(?:is\\s+)?[:\\-]?\\s*([A-Z0-9][A-Z0-9/\\-]{5,24})")
    private val tag = Regex("(?i)tag\\s*(?:id|no\\.?|number)\\s*(?:is\\s+)?[:\\-]?\\s*([A-Z0-9]{8,24})")
    private val account = Regex("(?i)(?:loan\\s+(?:a/c|account|acct|agreement)|a/c|account|acct|\\blan\\b)\\s*(?:(?:no|number|num)\\.?|#|id)?\\s*(?:is\\s+)?[:\\-]?\\s*((?:[X*•]{1,12}[ -]?)?[A-Z0-9]{4,20})")
    private val emi = Regex("(?i)\\bemi\\b[^0-9]{0,25}?(?:rs\\.?|inr|₹)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)")
    private val sum = Regex("(?i)sum\\s+insured[^0-9]{0,15}?([0-9][0-9,]*(?:\\.[0-9]{1,2})?)")
    private val insurers = Regex(
        "(?i)\\b(?:HDFC Ergo|ICICI Lombard|Bajaj Allianz|Tata AIG|Go Digit|Acko|Star Health|Care Health|Niva Bupa|Reliance General|SBI General|IFFCO Tokio|Royal Sundaram|Cholamandalam MS|Future Generali|New India Assurance|United India|Kotak General|Liberty General|Zuno|LIC|Max Life|HDFC Life|SBI Life)\\b",
    )
    private val insurerGeneric = Regex("((?:[A-Z][A-Za-z&]*\\s+){1,4}(?:(?:General|Life|Health)\\s+)?Insurance(?:\\s+(?:Company|Co\\.?|Ltd\\.?|Limited))?)")
    private val lenderGeneric = Regex("((?:[A-Z][A-Za-z&]*\\s+){0,2}(?:Bank|Finance|Finserv|Capital|Fincorp|Financial Services|Housing Finance))\\b")
    private val stop = setOf("Your", "Dear", "The", "From", "Of", "At", "With", "Loan", "Personal", "Home", "Car", "Auto", "Bike", "Two", "Wheeler", "Emi", "EMI", "Payment", "Motor", "Vehicle", "Renew", "Policy", "Is", "For", "Health", "Life", "General", "Travel", "Account", "Customer")
    private val product = Regex("(?i)warranty\\s+(?:card\\s+)?(?:for|on|of)\\s+(?:your\\s+|the\\s+)?([A-Za-z0-9][A-Za-z0-9 \\-+&'/]{2,48}?)(?=\\s+(?:is|are|has|have|will|valid|ends?|expires?|till|until|from|\\(|-)|[,.;\\n]|$)")
    private val perA = Regex("(?i)\\b(\\d{1,2})[ -]?(years?|yrs?|months?)\\b(?=[^.\\n]{0,25}warranty)")
    private val perB = Regex("(?i)warranty(?:\\s+period)?\\s*(?:of|:|-|is)?\\s*(\\d{1,2})[ -]?(years?|yrs?|months?)\\b")
    private val bought = Regex("(?i)(?:delivered|purchased|purchase|ordered|order|invoice|bought)(?:\\s+(?:on|date))?[^0-9a-z]{0,6}")
    private val span = Regex("(?i)\\b(?:from|period)\\b")
    private val join = Regex("(?i)\\s(?:to|till|until|upto|-|–)\\s")
    private val insLab = Regex("(?i)\\b(?:$BASE|renew(?:al)?|due)\\b")
    private val genLab = Regex("(?i)\\b(?:$BASE)\\b")
    private val loanLab = Regex("(?i)\\b(?:closure|maturity|last\\s+emi|end\\s+date)\\b")
    private val rx = linkedMapOf(
        DocKind.Puc to Regex("(?i)\\bpuc\\b|pollution\\s+(?:under\\s+control|certificate)"),
        DocKind.Fastag to Regex("(?i)fast?\\s?tag"),
        DocKind.Insurance to Regex("(?i)\\bpolic(?:y|ies)\\b|insurance|insurer"),
        DocKind.Rc to Regex("(?i)registration\\s+certificate|\\brc\\b|vehicle\\s+registration|parivahan|\\bvahan\\b"),
        DocKind.Loan to Regex("(?i)\\bloan\\b|\\bemi\\b"),
        DocKind.Warranty to Regex("(?i)warranty"),
    )
    private val off = mapOf(
        DocKind.Fastag to Regex("(?i)low\\s+balance|insufficient|recharge|\\btoll|debited|credited|balance"),
        DocKind.Loan to Regex("(?i)\\boffers?\\b|pre-?approved|eligible|apply\\s+now|congratulations|\\bget\\s+a?\\s*loan"),
    )

    fun kind(text: String): DocKind? = rx.entries.firstOrNull { (k, r) ->
        r.containsMatchIn(text) && off[k]?.containsMatchIn(text) != true && fields(k, text, epoch).let { it.number != null || it.expires != null || k == DocKind.Warranty && it.extra.isNotEmpty() }
    }?.key

    private fun veh(t: String) = vehicle.find(t)?.value
    private fun first(r: Regex, t: String, ok: (String) -> Boolean = { true }) = r.findAll(t).map { it.groupValues[1] }.firstOrNull(ok)
    private fun digits(s: String, n: Int) = s.count(Char::isDigit) >= n

    private fun org(r: Regex, t: String): String? = r.findAll(t).firstNotNullOfOrNull { m ->
        m.groupValues[1].trim().split(Regex("\\s+")).dropWhile { it in stop }.takeIf { it.size >= 2 }?.joinToString(" ")
    }

    private fun insurer(t: String) = insurers.find(t)?.value ?: org(insurerGeneric, t)

    private fun after(t: String, ref: LocalDate, label: Regex): LocalDate? {
        for (m in label.findAll(t)) {
            Dates.find(t, m.range.last + 1, ref)?.takeIf { it.first - m.range.last <= 14 }?.let { return it.second }
        }
        return null
    }

    private fun range(t: String, ref: LocalDate): LocalDate? {
        for (k in span.findAll(t)) {
            val a = Dates.find(t, k.range.last + 1, ref)?.takeIf { it.first - k.range.last <= 12 } ?: continue
            val c = join.find(t, a.first + 1) ?: continue
            Dates.find(t, c.range.last + 1, ref)?.takeIf { it.first - c.range.last <= 3 }?.let { return it.second }
        }
        return null
    }

    private fun expiry(t: String, ref: LocalDate, label: Regex) = range(t, ref) ?: after(t, ref, label)

    private fun period(t: String): Triple<Int, Boolean, String>? {
        val m = perA.find(t) ?: perB.find(t) ?: return null
        val n = m.groupValues[1].toInt()
        val months = m.groupValues[2].startsWith("m", true)
        return Triple(n, months, t.substring(m.groups[1]!!.range.first, m.groups[2]!!.range.last + 1))
    }

    private fun extra(vararg p: Pair<String, String?>) = p.mapNotNull { (k, v) -> v?.let { k to it } }.toMap()

    fun fields(kind: DocKind, text: String, ref: LocalDate): DocFields = when (kind) {
        DocKind.Insurance -> {
            val ins = insurer(text)
            DocFields(kind, ins ?: kind.label, first(policy, text) { digits(it, 1) }, expiry(text, ref, insLab), extra("vehicle" to veh(text), "sum_insured" to sum.find(text)?.groupValues?.get(1)))
        }
        DocKind.Rc -> DocFields(kind, "Registration certificate", veh(text), expiry(text, ref, genLab), emptyMap())
        DocKind.Puc -> DocFields(kind, "Pollution certificate", veh(text), expiry(text, ref, genLab), emptyMap())
        DocKind.Fastag -> {
            val id = first(tag, text) { digits(it, 1) }
            val v = veh(text)
            DocFields(kind, "FASTag", id ?: v, expiry(text, ref, genLab), extra("vehicle" to v.takeIf { id != null }))
        }
        DocKind.Loan -> {
            val lender = org(lenderGeneric, text)
            DocFields(kind, lender ?: kind.label, first(account, text) { digits(it, 4) }, expiry(text, ref, loanLab), extra("emi" to emi.find(text)?.groupValues?.get(1)))
        }
        DocKind.Warranty -> {
            val p = first(product, text)?.trim()
            val per = period(text)
            val ends = expiry(text, ref, genLab) ?: per?.let { (n, months, _) ->
                after(text, ref, bought)?.let { if (months) it.plusMonths(n.toLong()) else it.plusYears(n.toLong()) }
            }
            DocFields(kind, p?.let { "$it warranty" } ?: kind.label, null, ends, extra("product" to p, "period" to per?.third))
        }
        DocKind.Other -> DocFields(kind, "Document", null, expiry(text, ref, genLab), emptyMap())
    }
}

object DocNux {
    const val SUFFIX = "\n\n<|output|>\n"
    private const val CAP = 60

    private class Spec(val keys: List<String>, val nums: List<String>, val name: String?, val date: String?)

    private val specs = mapOf(
        DocKind.Insurance to Spec(listOf("policy_number", "insurer", "expiry_date", "vehicle_number", "sum_insured"), listOf("policy_number"), "insurer", "expiry_date"),
        DocKind.Rc to Spec(listOf("registration_number", "valid_till"), listOf("registration_number"), null, "valid_till"),
        DocKind.Puc to Spec(listOf("vehicle_number", "valid_till"), listOf("vehicle_number"), null, "valid_till"),
        DocKind.Fastag to Spec(listOf("tag_id", "vehicle_number"), listOf("tag_id", "vehicle_number"), null, null),
        DocKind.Loan to Spec(listOf("account_number", "lender", "emi_amount"), listOf("account_number"), "lender", null),
        DocKind.Warranty to Spec(listOf("product", "warranty_period", "ends_on"), emptyList(), "product", "ends_on"),
        DocKind.Other to Spec(listOf("title", "number", "expiry_date"), listOf("number"), "title", "expiry_date"),
    )

    private val space = Regex("\\s+")
    private val epoch = LocalDate.of(2000, 1, 1)

    private fun tpl(k: DocKind) = "{\n" + specs.getValue(k).keys.joinToString(",\n") { "    \"$it\": \"\"" } + "\n}"

    val templates: Map<DocKind, String> = DocKind.entries.associateWith(::tpl)

    fun grammar(kind: DocKind): String {
        val body = specs.getValue(kind).keys.joinToString(" \",\" ws ") { "\"\\\"$it\\\":\" ws s" }
        return "root ::= \"{\" ws $body ws \"}\"\ns ::= \"\\\"\" c{0,$CAP} \"\\\"\"\nc ::= [^\"\\\\\\n]\nws ::= [ \\t\\n]*\n"
    }

    fun prefix(kind: DocKind) = "<|input|>\n### Template:\n${templates.getValue(kind)}\n### Text:\n"

    fun prompt(kind: DocKind, text: String) = prefix(kind) + Nux.clip(text) + SUFFIX

    private fun norm(s: String) = space.replace(s.trim(), " ")

    private fun date(v: String, ref: LocalDate): LocalDate? = runCatching { LocalDate.parse(v) }.getOrNull() ?: Dates.first(v, ref)

    fun parse(kind: DocKind, json: String, text: String): Map<String, String> {
        val m = runCatching { Json.obj(json.trim()) }.getOrNull() ?: return emptyMap()
        val s = specs.getValue(kind)
        val src = norm(text)
        return buildMap {
            for (k in s.keys) {
                val v = (m[k] as? String)?.let(::norm)?.takeIf { it.isNotEmpty() && it.length <= CAP } ?: continue
                if (src.contains(v) || k == s.date && date(v, epoch) != null) put(k, v)
            }
        }
    }

    fun fields(kind: DocKind, parsed: Map<String, String>, ref: LocalDate): DocFields {
        val s = specs.getValue(kind)
        val num = s.nums.firstNotNullOfOrNull { parsed[it] }
        val name = s.name?.let { parsed[it] }
        val title = when (kind) {
            DocKind.Rc -> "Registration certificate"
            DocKind.Puc -> "Pollution certificate"
            DocKind.Fastag -> "FASTag"
            DocKind.Warranty -> name?.let { "$it warranty" } ?: kind.label
            DocKind.Other -> name ?: "Document"
            else -> name ?: kind.label
        }
        return DocFields(kind, title, num, s.date?.let { parsed[it] }?.let { date(it, ref) }, parsed.filter { (k, v) -> k != s.name && k != s.date && v != num })
    }
}
