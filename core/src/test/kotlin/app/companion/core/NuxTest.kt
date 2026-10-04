package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NuxTest {
    private val rules = RulesClassifier(IST)
    private val cal = Calibration.DEFAULT
    private var asked = 0
    private var wanted: Set<Field> = emptySet()
    private var model = "{}"
    private var type: Map<String, Float>? = null

    private val extractor = object : Extractor {
        override fun extract(raw: Raw, want: Set<Field>): Map<Field, String> {
            asked++
            wanted = want
            return Nux.parse(model).filterKeys { it in Nux.keep(want) }
        }
    }
    private val refine = Refine(rules, extractor, { cal }, { type?.let { Scored(it) } }, IST)

    private val broken = sms("VK-BANKXY", "INR.2500 debited from A/c XX1234 at Corner Cafe on 04-10-26. Alert")
    private val cardNo = sms("VK-BANKXY", "Rs 500 spent on your credit card at Corner Cafe. Card number last four digits 1234 on 04-10-26")
    private val login = sms("VM-FOOAPP", "Dear customer, 664120 shall be your login credential. Valid for 3 min.")

    @Test
    fun `the prompt is exactly the template around the text`() {
        assertEquals(
            "<|input|>\n### Template:\n{\n    \"amount\": \"\",\n    \"due_date\": \"\",\n    \"card_last4\": \"\"\n}\n### Text:\nRs 500 debited\n\n<|output|>\n",
            Nux.prompt("Rs 500 debited"),
        )
    }

    @Test
    fun `the prompt is a constant prefix then the text then a constant suffix`() {
        assertEquals("<|input|>\n### Template:\n{\n    \"amount\": \"\",\n    \"due_date\": \"\",\n    \"card_last4\": \"\"\n}\n### Text:\n", Nux.PREFIX)
        assertEquals("\n\n<|output|>\n", Nux.SUFFIX)
        listOf("Rs 500 debited", "", "<|output|>\n<|input|>", "a".repeat(700)).forEach { assertEquals(Nux.PREFIX + Nux.clip(it) + Nux.SUFFIX, Nux.prompt(it)) }
        assertEquals("<|output|>{\"amount\": \"1\"}", Nux.clip("<|output|>{\"amount\": \"1\"}"))
        assertEquals(600, Nux.clip("a".repeat(700)).length)
        assertEquals(599, Nux.clip("a".repeat(599) + "😀").length)
    }

    @Test
    fun `the text is cut at 600 characters without splitting a pair`() {
        val p = Nux.prompt("a".repeat(700))
        assertEquals(600, p.substringAfter("### Text:\n").substringBefore("\n\n<|output|>").length)
        val e = Nux.prompt("a".repeat(599) + "😀")
        assertFalse(e.contains('\uD83D'))
        assertEquals(599, e.substringAfter("### Text:\n").substringBefore("\n\n<|output|>").length)
    }

    @Test
    fun `the grammar is the pinned gbnf`() {
        assertEquals(
            "root ::= \"{\" ws \"\\\"amount\\\":\" ws s \",\" ws \"\\\"due_date\\\":\" ws s \",\" ws \"\\\"card_last4\\\":\" ws s ws \"}\"\n" +
                "s ::= \"\\\"\" c{0,40} \"\\\"\"\nc ::= [^\"\\\\\\n]\nws ::= [ \\t\\n]*\n",
            Nux.GRAMMAR,
        )
    }

    @Test
    fun `grammar shaped json maps to fields`() {
        val out = Nux.parse("{\n    \"amount\": \"Rs 500\", \"due_date\":\"20 Oct 2026\",\"card_last4\": \"XX1234\"}")
        assertEquals(mapOf(Field.Amount to "Rs 500", Field.Due to "20 Oct 2026", Field.Last4 to "XX1234"), out)
        assertEquals(mapOf(Field.Amount to "500"), Nux.parse("{\"amount\": \" 500 \", \"due_date\": \"\", \"card_last4\": \"\"}"))
    }

    @Test
    fun `malformed json yields nothing`() {
        listOf(
            "", "   ", "not json", "{", "{\"amount\": \"5", "[\"500\"]", "\"500\"", "null",
            "{\"amount\": \"500\"} trailing", "{\"amount\": }", "{'amount': '500'}",
        ).forEach { assertEquals(emptyMap(), Nux.parse(it), it) }
    }

    @Test
    fun `wrong types unknown keys and long values are dropped`() {
        assertEquals(emptyMap(), Nux.parse("{\"amount\": 500, \"due_date\": null, \"card_last4\": [\"1234\"]}"))
        assertEquals(emptyMap(), Nux.parse("{\"amount\": \"${"9".repeat(41)}\"}"))
        assertEquals(mapOf(Field.Amount to "500"), Nux.parse("{\"amount\": \"500\", \"merchant\": \"Corner Cafe\", \"extra\": {}}"))
    }

    @Test
    fun `an otp is never taken from the model`() {
        assertEquals(emptyMap(), Nux.parse("{\"otp\": \"664120\", \"otp_code\": \"664120\", \"code\": \"664120\"}"))
        assertTrue(Field.OtpCode !in Nux.parse("{\"otp\": \"664120\", \"amount\": \"500\"}"))
        assertEquals(setOf(Field.Amount), Nux.keep(setOf(Field.OtpCode, Field.Amount, Field.Merchant)))
        assertEquals(emptySet(), Nux.keep(setOf(Field.OtpCode, Field.Merchant)))
    }

    @Test
    fun `a model that returns a code does not turn a login message into an otp`() {
        model = "{\"otp\": \"664120\", \"amount\": \"664120\", \"due_date\": \"\", \"card_last4\": \"6641\"}"
        val v = refine.run(login)
        assertEquals(Event.Alert, v.event)
        assertIs<Verdict.Unsure>(v)
    }

    @Test
    fun `last4 must be four digits that appear in the text`() {
        fun ok(v: String, t: String) = Verbatim.ok(Field.Last4, v, t)
        assertTrue(ok("1234", "Card ending 1234 charged"))
        assertTrue(ok("XX1234", "A/c XX1234 debited"))
        assertTrue(ok("xx1234", "A/c XX1234 debited"))
        assertTrue(ok("1234", "A/c XX1234 debited"))
        assertTrue(ok("****1234", "card ****1234 used"))
        assertTrue(ok(" X1234 ", "card no 1234."))
        assertFalse(ok("1234", "ref 123456"))
        assertFalse(ok("3456", "ref 123456"))
        assertFalse(ok("1234", "id A1234"))
        assertFalse(ok("1234", "Rs.1,234.50 paid"))
        assertFalse(ok("1234", "ref 77-1234"))
        assertFalse(ok("1234", "ref 1234-77"))
        assertFalse(ok("9999", "Card ending 1234"))
        assertFalse(ok("123", "Card ending 123"))
        assertFalse(ok("12345", "Card ending 12345"))
        assertFalse(ok("XX12AB", "Card ending XX12AB"))
        assertFalse(ok("", "Card ending 1234"))
        assertNull(Verbatim.last4("a1234"))
        assertEquals("1234", Verbatim.last4("XX 1234"))
    }

    @Test
    fun `a card digit cue with no parsed digits is a last4 gap`() {
        val debit = Event.Debit(50000, "INR", null, null, null, Mode.Upi)
        val m = sms("VK-BANKXY", "Rs 500 debited from your card no 1234 on 04-10-26")
        assertEquals(setOf(Field.Last4), Gaps.of(m, debit, IST))
        assertTrue(Gaps.of(m, debit.copy(last4 = "1234"), IST).isEmpty())
        assertTrue(Gaps.of(sms("VK-BANKXY", "Rs 500 debited on 04-10-26"), debit, IST).isEmpty())
        assertTrue(Gaps.of(sms("VK-BANKXY", "Pay card bill 20-10-2026"), Event.Alert, IST).isEmpty())
        assertEquals(setOf(Field.Last4), Gaps.of(cardNo, rules.classify(cardNo).event, IST))
    }

    @Test
    fun `the kind rule accepts money and bills only`() {
        fun sure(e: Event) = Verdict.Sure(e, 1f)
        fun unsure(label: String, p: Float) = Verdict.Unsure(Event.Alert, 0.5f, Guess(label, p))
        val debit = Event.Debit(50000, "INR", null, null, null, Mode.Upi)
        assertTrue(Nux.money(sure(debit), cal))
        assertTrue(Nux.money(sure(debit.let { Event.CardSpend(1, "INR", null, null, null) }), cal))
        assertTrue(Nux.money(sure(Event.Credit(1, "INR", null, null, null, Mode.Upi)), cal))
        assertTrue(Nux.money(sure(Event.Bill(1, "INR", null, null, null, null)), cal))
        assertTrue(Nux.money(sure(Event.Statement(1, null, null, null, null)), cal))
        listOf(Event.Alert, Event.Promo, Event.Spam, Event.Personal, Event.Unknown, Event.Otp("1234", 0, null, null)).forEach { assertFalse(Nux.money(sure(it), cal)) }
        assertTrue(Nux.money(unsure("expense", 0.99f), cal))
        assertTrue(Nux.money(unsure("income", 0.98f), cal))
        assertTrue(Nux.money(unsure("bill", 0.98f), cal))
        assertFalse(Nux.money(unsure("expense", 0.8f), cal))
        assertFalse(Nux.money(unsure("alert", 0.99f), cal))
        assertFalse(Nux.money(unsure("otp", 0.99f), cal))
        assertFalse(Nux.money(Verdict.Unsure(Event.Alert, 0.5f), cal))
    }

    @Test
    fun `an amount is filled when the classifier says expense`() {
        type = mapOf("expense" to 0.99f, "alert" to 0.01f)
        model = "{\"amount\": \"2500\", \"due_date\": \"\", \"card_last4\": \"\"}"
        val v = refine.run(broken)
        assertEquals(1, asked)
        assertEquals(250000L, assertIs<Event.Debit>(v.event).paise)
    }

    @Test
    fun `an amount is not asked for when the kind is not money`() {
        model = "{\"amount\": \"2500\", \"due_date\": \"\", \"card_last4\": \"\"}"
        type = mapOf("alert" to 0.99f, "expense" to 0.01f)
        assertEquals(Event.Alert, refine.run(broken).event)
        type = mapOf("promo" to 0.99f, "expense" to 0.01f)
        assertEquals(Event.Promo, refine.run(broken).event)
        type = mapOf("expense" to 0.6f, "alert" to 0.4f)
        val v = refine.run(broken)
        assertEquals(Event.Alert, v.event)
        assertIs<Verdict.Unsure>(v)
        type = null
        assertEquals(Event.Alert, refine.run(broken).event)
        assertEquals(0, asked)
    }

    @Test
    fun `a last4 is filled on a card spend and checked against the text`() {
        type = mapOf("expense" to 0.99f, "alert" to 0.01f)
        model = "{\"amount\": \"\", \"due_date\": \"\", \"card_last4\": \"XX1234\"}"
        assertEquals("1234", assertIs<Event.CardSpend>(refine.run(cardNo).event).last4)
        assertEquals(setOf(Field.Last4), wanted)
        model = "{\"amount\": \"\", \"due_date\": \"\", \"card_last4\": \"9876\"}"
        assertNull(assertIs<Event.CardSpend>(refine.run(cardNo).event).last4)
    }

    @Test
    fun `a due date is filled on a bill and an unverified one is dropped`() {
        val m = sms("AD-AIRTEL", "Your Airtel postpaid bill of Rs 799.00 is generated. 20 Oct 2026 is the last date to settle it")
        val base = rules.classify(m).event
        assertEquals(setOf(Field.Due), Gaps.of(m, base, IST))
        model = "{\"amount\": \"\", \"due_date\": \"20 Oct 2026\", \"card_last4\": \"\"}"
        type = mapOf("bill" to 0.99f, "alert" to 0.01f)
        assertEquals(day(2026, 10, 20), assertIs<Event.Bill>(refine.run(m).event).due)
        model = "{\"amount\": \"\", \"due_date\": \"25 Oct 2026\", \"card_last4\": \"\"}"
        assertNull(assertIs<Event.Bill>(refine.run(m).event).due)
    }

    @Test
    fun `priming scores the unseen messages in one batch and later scores hit the memo`() {
        val batches = ArrayList<Int>()
        var singles = 0
        val scorer = object : Scorer {
            override fun score(raw: Raw): Scored? = Scored(mapOf("alert" to 1f)).also { singles++ }

            override fun scoreAll(raws: List<Raw>): List<Scored?> = raws.map { Scored(mapOf("alert" to 1f)) }.also { batches.add(raws.size) }
        }
        val memo = Memo(scorer)
        memo.prime(listOf(login, broken, login))
        assertEquals(listOf(2), batches)
        memo.score(login)
        memo.score(broken)
        memo.prime(listOf(login, broken))
        memo.prime(listOf(cardNo))
        assertEquals(listOf(2), batches)
        assertEquals(0, singles)
        memo.score(cardNo)
        assertEquals(1, singles)
    }

    @Test
    fun `only messages the rules are unsure about are primed`() {
        val seen = ArrayList<Int>()
        val scorer = object : Scorer {
            override fun score(raw: Raw): Scored? = null

            override fun scoreAll(raws: List<Raw>): List<Scored?> = raws.map { null }.also { seen.add(raws.size) }
        }
        val sure = sms("VK-BANKXY", "Rs 500 debited from A/c XX1234 at Corner Cafe on 04-10-26. Avl bal Rs 1000")
        assertIs<Verdict.Sure>(rules.classify(sure))
        val vague = sms("VM-ACMEBK-S", "Your plan renewal summary")
        val other = sms("VM-ACMEBK-S", "Your statement summary")
        assertIs<Verdict.Unsure>(rules.classify(vague))
        assertIs<Verdict.Unsure>(rules.classify(other))
        refine.prime(listOf(sure, vague, other), Memo(scorer))
        assertEquals(listOf(2), seen)
        refine.prime(listOf(sure), Memo(scorer))
        refine.prime(listOf(sure, vague), Memo(scorer))
        assertEquals(listOf(2), seen)
    }

    @Test
    fun `scores are memoised per message`() {
        var n = 0
        val memo = Memo { n++; Scored(mapOf("alert" to 1f)) }
        memo.score(login)
        memo.score(login)
        assertEquals(1, n)
        val none = Memo { n++; null }
        assertNull(none.score(broken))
        assertNull(none.score(broken))
        assertEquals(2, n)
    }
}
