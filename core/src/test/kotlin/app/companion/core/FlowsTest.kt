package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlowsTest {
    private fun debit(last4: String? = "1234", merchant: String? = null) = Event.Debit(10000, "INR", last4, "Acme Bank", merchant, Mode.Upi)

    private fun credit(last4: String? = "1234", mode: Mode = Mode.Other) = Event.Credit(10000, "INR", last4, "Acme Bank", null, mode)

    private fun spend() = Event.CardSpend(10000, "INR", "4321", "Acme Bank", "ACME STORE")

    private val own = setOf("1234", "5678")

    @Test
    fun `a plain payment to a shop is spend`() {
        assertNull(Flows.of(debit(merchant = "ACME STORE"), "Rs 100 debited from A/c XX1234 to ACME STORE on 04-Oct-26", own))
        assertNull(Flows.of(spend(), "Rs 100 spent on card XX4321 at ACME STORE", own))
    }

    @Test
    fun `a payment to a person stays spend`() {
        assertNull(Flows.of(debit(merchant = "RAVI KUMAR"), "Rs 100 debited from A/c XX1234 to RAVI KUMAR via UPI", own))
    }

    @Test
    fun `card bill payments in every phrasing`() {
        listOf(
            "Payment of Rs 5,000 received towards your ACME Bank Credit Card XX4321",
            "Payment received for your credit card ending 4321",
            "Rs 5000 debited for credit card bill payment",
            "Rs 5000 debited for CC payment via BillDesk",
            "Rs 5000 paid to CRED for credit card",
            "Rs 5000 paid via UPI to CREDCLUB",
            "Rs 5000 paid towards your ACME credit card",
            "CC PAYMENT of Rs 5000 done",
        ).forEach { assertEquals(Flow.CardBill, Flows.of(debit(), it, own), it) }
    }

    @Test
    fun `a payment received credit on a card is not income`() {
        assertEquals(Flow.CardBill, Flows.of(credit("4321", Mode.Card), "Payment of Rs 5,000 received on your ACME card XX4321", own))
        assertEquals(Flow.CardBill, Flows.of(credit("4321"), "Payment of Rs 5,000 received towards your credit card XX4321", own))
    }

    @Test
    fun `a salary credit is not a card bill`() {
        assertNull(Flows.of(credit(), "Rs 50,000 credited to A/c XX1234 salary payment received from ACME LTD", own))
    }

    @Test
    fun `self transfer wording`() {
        listOf("Rs 100 sent to own account", "Self transfer of Rs 100", "Rs 100 transfer to self", "Rs 100 moved to your own a/c").forEach {
            assertEquals(Flow.Self, Flows.of(debit(), it, emptySet()), it)
        }
    }

    @Test
    fun `a counterparty last4 in the user's own set is a self transfer`() {
        assertEquals(Flow.Self, Flows.of(debit(), "Rs 100 debited from A/c XX1234 and transferred to A/c XX5678", own))
        assertEquals(Flow.Self, Flows.of(debit(), "Rs 100 debited from A/c XX1234 to account ending 5678", own))
        assertEquals(Flow.Self, Flows.of(credit("5678"), "Rs 100 credited to A/c XX5678 from A/c XX1234", own))
    }

    @Test
    fun `an unknown counterparty or the event's own account is not self`() {
        assertNull(Flows.of(debit(), "Rs 100 debited from A/c XX1234 and transferred to A/c XX9999", own))
        assertNull(Flows.of(debit(), "Rs 100 debited from A/c XX1234 to A/c XX5678", setOf("1234")))
        assertNull(Flows.of(debit("5678"), "Rs 100 debited from A/c XX1234 to A/c XX5678", setOf("5678")))
    }

    @Test
    fun `credit direction reads the from side`() {
        assertEquals(setOf("1234"), Flows.counterparty(credit("5678"), "credited to A/c XX5678 from A/c XX1234"))
        assertEquals(setOf("5678"), Flows.counterparty(debit(), "debited from A/c XX1234 to A/c XX5678"))
    }

    @Test
    fun `own last4s keep only four digits`() {
        assertEquals(setOf("1234", "5678"), Flows.own(listOf("1234", null, "12", "abcd", "5678", "1234")))
    }

    @Test
    fun `investments`() {
        listOf(
            "Rs 5000 debited towards SIP in ACME Fund",
            "Mutual fund purchase of Rs 5000",
            "Units allotted at NAV 12.5",
            "Rs 5000 paid to ZERODHA BROKING",
            "Rs 5000 to Groww",
            "Rs 5000 to Kuvera",
            "Rs 5000 to Zerodha Coin",
            "Rs 5000 to ICCL",
            "Rs 5000 to NSE Clearing",
            "Rs 5000 BSE StAR MF order",
            "Rs 5000 PPF contribution",
            "NPS contribution of Rs 5000",
            "MF purchase Rs 5000",
        ).forEach { assertEquals(Flow.Invest, Flows.of(debit(), it, own), it) }
        assertEquals(Flow.Invest, Flows.of(spend(), "Rs 5000 spent on card XX4321 at GROWW", own))
    }

    @Test
    fun `similar looking words are not investments`() {
        listOf("Rs 100 at COINBASE CAFE", "Rs 100 at NAVRATNA SWEETS", "Rs 100 at SIPPER CLUB", "Rs 100 at MFG STORE").forEach {
            assertNull(Flows.of(debit(), it, own), it)
        }
    }

    @Test
    fun `card spends are never a card bill or self`() {
        assertNull(Flows.of(spend(), "Rs 100 spent on card XX4321 at CRED CLUB credit card", own))
    }

    @Test
    fun `only self and card bill credits leave the income total`() {
        assertFalse(Flows.income(Flow.Self.name))
        assertFalse(Flows.income(Flow.CardBill.name))
        assertTrue(Flows.income(Flow.Invest.name))
        assertTrue(Flows.income(null))
    }
}
