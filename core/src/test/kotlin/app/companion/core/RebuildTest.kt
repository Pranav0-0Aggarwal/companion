package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RebuildTest {
    private fun of(m: Raw, e: Event, vararg got: Pair<Field, String>) = Rebuild.of(m, e, mapOf(*got), IST)

    private val login = sms("VM-FOOAPP", "Dear customer, 664120 shall be your login credential. Valid for 3 min.")
    private val debit = Event.Debit(50000, "INR", "1234", null, null, Mode.Upi)

    @Test
    fun `a verified code becomes an otp with the expiry the rules read`() {
        val e = assertIs<Event.Otp>(of(login, Event.Alert, Field.OtpCode to "664120"))
        assertEquals("664120", e.code)
        assertEquals(NOW + 180_000, e.expiresAt)
        assertEquals("login", e.purpose)
    }

    @Test
    fun `an otp without a stated validity gets the default expiry`() {
        val m = sms("VM-FOOAPP", "Use 482913 to verify your Foo account")
        assertEquals(NOW + 600_000, assertIs<Event.Otp>(of(m, Event.Alert, Field.OtpCode to "482913")).expiresAt)
    }

    @Test
    fun `a code that is not a free token is not turned into an otp`() {
        assertEquals(Event.Alert, of(login, Event.Alert, Field.OtpCode to "6641"))
        assertEquals(Event.Alert, of(login, Event.Alert, Field.OtpCode to "999999"))
        val pin = sms("VM-FOOAPP", "Verify your delivery to PIN code 560001")
        assertEquals(Event.Alert, of(pin, Event.Alert, Field.OtpCode to "560001"))
        val acct = sms("VM-FOOAPP", "Verify login on A/c XX4821")
        assertEquals(Event.Alert, of(acct, Event.Alert, Field.OtpCode to "4821"))
    }

    @Test
    fun `an existing otp and a no op merge stay as they are`() {
        val otp = Event.Otp("111111", NOW, null, null)
        assertEquals(otp, of(login, otp, Field.OtpCode to "664120"))
        assertEquals(debit, of(login, debit))
    }

    @Test
    fun `an amount lets the rules rebuild the move`() {
        val m = sms("VK-BANKXY", "INR.2500 debited from A/c XX1234 at Corner Cafe on 04-10-26. Alert")
        val e = assertIs<Event.Debit>(of(m, Event.Alert, Field.Amount to "2500"))
        assertEquals(250000L, e.paise)
        assertEquals("INR", e.currency)
        assertEquals("1234", e.last4)
        assertEquals("Corner Cafe", e.merchant)
    }

    @Test
    fun `an amount does not rebuild a message the rules refuse as a move`() {
        val m = sms("VK-BANKXY", "INR.2500 debit failed due to insufficient funds")
        assertEquals(Event.Alert, of(m, Event.Alert, Field.Amount to "2500"))
    }

    @Test
    fun `an amount never overrides a move that already has one`() {
        assertEquals(debit, of(sms("VK-BANKXY", "Rs 500 debited, Rs 900 hold"), debit, Field.Amount to "900"))
    }

    @Test
    fun `an unreadable amount value changes nothing`() {
        val m = sms("VK-BANKXY", "INR.2500 debited from A/c XX1234")
        assertEquals(Event.Alert, of(m, Event.Alert, Field.Amount to "lots"))
    }

    @Test
    fun `a merchant fills only an empty slot`() {
        val m = sms("VK-BANKXY", "Rs 500 debited. Paid to Zed Mart via UPI")
        assertEquals(debit.copy(merchant = "Zed Mart"), of(m, debit, Field.Merchant to "Zed Mart"))
        assertEquals(debit.copy(merchant = "Other"), of(m, debit.copy(merchant = "Other"), Field.Merchant to "Zed Mart"))
        val card = Event.CardSpend(50000, "INR", "1234", null, null)
        assertEquals(card.copy(merchant = "Zed Mart"), of(m, card, Field.Merchant to "Zed Mart"))
        val credit = Event.Credit(50000, "INR", "1234", null, null, Mode.Upi)
        assertEquals(credit.copy(merchant = "Zed Mart"), of(m, credit, Field.Merchant to "Zed Mart"))
        assertEquals(Event.Alert, of(m, Event.Alert, Field.Merchant to "Zed Mart"))
    }

    @Test
    fun `a merchant the rules would refuse as a name is dropped`() {
        val m = sms("VK-BANKXY", "Rs 500 debited. Paid to Cash Mart via UPI")
        assertEquals(debit, of(m, debit, Field.Merchant to "Cash Mart"))
        assertEquals(debit, of(m, debit, Field.Merchant to "UPI"))
    }

    @Test
    fun `a due date fills a bill or statement that lacks one`() {
        val m = sms("VK-BANKXY", "Your Foo bill of Rs 1,200 is ready. Pay by 20 Oct 2026")
        val bill = Event.Bill(120000, "INR", null, null, "Foo", null)
        assertEquals(bill.copy(due = day(2026, 10, 20)), of(m, bill, Field.Due to "20 Oct 2026"))
        assertEquals(bill.copy(due = day(2026, 11, 1)), of(m, bill.copy(due = day(2026, 11, 1)), Field.Due to "20 Oct 2026"))
        val st = Event.Statement(450000, null, null, "1234", "Foo")
        assertEquals(st.copy(due = day(2026, 10, 20)), of(m, st, Field.Due to "20 Oct 2026"))
        assertEquals(debit, of(m, debit, Field.Due to "20 Oct 2026"))
    }

    @Test
    fun `amount and due together rebuild a bill from an unread message`() {
        val m = sms("VK-BANKXY", "Your Foo electricity bill is generated. Pay INR.1200 by 20 Oct 2026")
        val e = assertIs<Event.Bill>(of(m, Event.Unknown, Field.Amount to "1200", Field.Due to "20 Oct 2026"))
        assertEquals(120000L, e.paise)
        assertEquals(day(2026, 10, 20), e.due)
    }

    @Test
    fun `an otp wins over other fields`() {
        assertIs<Event.Otp>(of(login, Event.Alert, Field.OtpCode to "664120", Field.Amount to "180"))
    }
}
