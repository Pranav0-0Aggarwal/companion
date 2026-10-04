package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RefineTest {
    private val rules = RulesClassifier(IST)
    private var asked = 0
    private var scored = 0
    private var gave: Map<Field, String> = emptyMap()
    private var want: Set<Field> = emptySet()
    private var type: Map<String, Float>? = null

    private val extractor = object : Extractor {
        override fun extract(raw: Raw, want: Set<Field>): Map<Field, String> {
            asked++
            this@RefineTest.want = want
            return gave
        }
    }
    private val scorer = Scorer { scored++; type?.let { Scored(it) } }
    private val refine = Refine(rules, extractor, { Calibration.DEFAULT }, scorer, IST)

    private val login = sms("VM-FOOAPP", "Dear customer, 664120 shall be your login credential. Valid for 3 min.")
    private val clean = sms("VK-BANKXY", "Rs 500 debited from A/c XX1234 on 04-10-26. Avl bal Rs 9,000.50")

    @Test
    fun `a sure message with no gap touches neither the extractor nor the scorer`() {
        val v = refine.run(clean)
        assertIs<Verdict.Sure>(v)
        assertEquals(0, asked)
        assertEquals(0, scored)
    }

    @Test
    fun `a verified code makes a sure otp and skips the scorer`() {
        gave = mapOf(Field.OtpCode to "664120")
        val v = refine.run(login)
        assertEquals(setOf(Field.OtpCode), want)
        assertIs<Verdict.Sure>(v)
        assertEquals("664120", assertIs<Event.Otp>(v.event).code)
        assertEquals(0, scored)
    }

    @Test
    fun `values that fail the verbatim check are dropped before the rebuild`() {
        gave = mapOf(Field.OtpCode to "123456", Field.Amount to "500", Field.Merchant to "Imaginary Shop")
        val v = refine.run(login)
        assertEquals(1, asked)
        assertEquals(Event.Alert, v.event)
        assertIs<Verdict.Unsure>(v)
    }

    @Test
    fun `fields that were not asked for are ignored`() {
        gave = mapOf(Field.Merchant to "Corner Cafe", Field.OtpCode to "664120")
        val m = sms("VK-BANKXY", "Welcome to Corner Cafe rewards")
        refine.run(m)
        assertEquals(0, asked)
        gave = mapOf(Field.Amount to "664120")
        val v = refine.run(login)
        assertEquals(Event.Alert, v.event)
    }

    @Test
    fun `an unsure message with no gap goes to the scorer and keeps the rules event`() {
        type = mapOf("alert" to 0.99f, "spam" to 0.01f)
        val m = sms("VM-FOOAPP", "Your Foo account details were updated")
        val v = refine.run(m)
        assertEquals(1, scored)
        assertEquals(0, asked)
        assertIs<Verdict.Sure>(v)
        assertEquals(Event.Alert, v.event)
    }

    @Test
    fun `no scorer result keeps the rules verdict`() {
        val m = sms("VM-FOOAPP", "Your Foo account details were updated")
        assertEquals(rules.classify(m), refine.run(m))
        assertEquals(1, scored)
    }

    @Test
    fun `a rebuilt move is scored by the rules like any other move`() {
        gave = mapOf(Field.Amount to "2500", Field.Merchant to "Corner Cafe")
        val m = sms("VK-BANKXY", "INR.2500 debited from A/c XX1234 at Corner Cafe on 04-10-26. Alert")
        val v = refine.run(m)
        val e = assertIs<Event.Debit>(v.event)
        assertEquals(250000L, e.paise)
        assertEquals(rules.judge(e, m).confidence, v.confidence)
    }

    @Test
    fun `chats never reach the extractor`() {
        gave = mapOf(Field.OtpCode to "664120")
        refine.run(Raw(Source.Wa, "Asha", "", "login 664120 verify", NOW))
        assertEquals(0, asked)
    }

    @Test
    fun `an otp event is lost only by an explicit refile`() {
        val otp = Filed("Otp", null, null, Refile.SETTLED)
        val alert = Filed("Alert", null, null, Refile.ASK)
        assertTrue(Refile.lost(otp, alert))
        assertTrue(!Refile.lost(alert, otp))
        assertTrue(!Refile.lost(alert, alert.copy(kind = "Spam")))
        assertTrue(Refile.lost(Filed("Alert", "otp", null, Refile.ASK), alert))
    }
}
