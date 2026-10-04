package app.companion.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardPayTest {
    @Test
    fun `a bills debit to a card issuer or CRED is a card payment`() {
        listOf("CRED", "Cred Club", "SBI Card", "SBI Cards & Payment", "HDFC Bank Credit Card", "ICICI Bank Card", "Amex", "OneCard", "Axis Bank cc").forEach {
            assertTrue(CardPay.of("Debit", "bills", it), it)
        }
    }

    @Test
    fun `other bills are spend`() {
        listOf("Airtel", "HDFC Life", "BESCOM", "ACME Power", "Axis Bank", "Credit Suisse Rent").forEach {
            assertFalse(CardPay.of("Debit", "bills", it), it)
        }
    }

    @Test
    fun `only a debit in bills counts`() {
        assertFalse(CardPay.of("Debit", "transfer", "CRED"))
        assertFalse(CardPay.of("Debit", null, "CRED"))
        assertFalse(CardPay.of("CardSpend", "bills", "CRED"))
        assertFalse(CardPay.of("Credit", "bills", "CRED"))
        assertFalse(CardPay.of("Debit", "bills", null))
    }
}
