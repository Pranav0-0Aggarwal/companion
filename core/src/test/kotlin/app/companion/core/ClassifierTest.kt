package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ClassifierTest {
    private val c = RulesClassifier(IST)

    @Test
    fun `otp is sure`() {
        val v = assertIs<Verdict.Sure>(c.classify(sms("VM-HDFCBK", "123456 is your OTP. Valid for 5 minutes.")))
        assertEquals("123456", assertIs<Event.Otp>(v.event).code)
    }

    @Test
    fun `identified debit is sure`() {
        assertIs<Verdict.Sure>(c.classify(sms("VM-HDFCBK", "Rs.240.00 debited from a/c XX1234 on 04-10-26 to VPA bluetokai@okhdfcbank (UPI Ref No 427190123456)")))
    }

    @Test
    fun `debit with nothing to identify it asks`() {
        val v = assertIs<Verdict.Unsure>(c.classify(sms("VM-HDFCBK", "Rs 500 debited")))
        assertIs<Event.Debit>(v.event)
    }

    @Test
    fun `business text with an amount asks`() {
        val v = assertIs<Verdict.Unsure>(c.classify(sms("VM-POLICY", "Rs 4,500 premium for your policy needs attention")))
        assertEquals(Event.Alert, v.event)
    }

    @Test
    fun `unknown is never sure`() {
        assertIs<Verdict.Unsure>(c.classify(sms("VM-ACME", "Your plan renewal summary")))
        assertIs<Verdict.Unsure>(c.classify(sms("VM-ACME", "Rs 4,500 premium for your plan needs attention")))
    }

    @Test
    fun `card spend with a merchant is sure`() {
        assertIs<Verdict.Sure>(c.classify(sms("AD-SLCEIT-S", "Your ACME credit card transaction of Rs. 111 on Shopco is successful. Questions? Call 000")))
        assertIs<Verdict.Sure>(c.classify(sms("VM-SBICRD-S", "Rs.111.00 spent on your ACME Credit Card ending 1234 at Shopco on 01/01/30. Unrecognised? Write to https://acme.example/x")))
    }

    @Test
    fun `debit with one identifier is sure and with none asks`() {
        assertIs<Verdict.Sure>(c.classify(sms("VM-HDFCBK-S", "Rs 450 debited from a/c XX1234 on 04-10-26")))
        assertIs<Verdict.Unsure>(c.classify(sms("VM-HDFCBK-S", "Rs 450 debited on 04-10-26")))
    }

    @Test
    fun `risky money words keep a debit unsure`() {
        assertIs<Verdict.Unsure>(c.classify(sms("VM-PNBSMS-S", "A/c XX5508 debited Rs.3846.00 for UPI Autopay on SARTHAK")))
        assertIs<Verdict.Unsure>(c.classify(sms("VM-HDFCBK-S", "Rs 450 debited from a/c XX1234 to shop@icici. Txn failed, refund in 3 days")))
    }

    @Test
    fun `credit into an account is sure but a card credit asks`() {
        assertIs<Verdict.Sure>(c.classify(sms("VM-HDFCBK-S", "Rs. 111.00 received in a/c XX1234 from ACME TRADERS on 01-Jan-30 (NEFT Ref No 000111222333)")))
        assertIs<Verdict.Unsure>(c.classify(sms("VM-ICICIT-S", "Refund of Rs 111.00 from Shopco credited to ACME Bank Credit Card XX9876 on 01-JAN-30")))
    }

    @Test
    fun `money requests and future debits are not expenses`() {
        val v = c.classify(sms("VM-PHONPE-S", "Shopco has requested money from you. Rs.111 will be debited on approval"))
        assertIs<Verdict.Unsure>(v)
        assertEquals(Event.Alert, v.event)
    }

    @Test
    fun `promotional sender with an offer is sure promo`() {
        assertIs<Verdict.Sure>(c.classify(sms("VM-SHOPAP-P", "Festive sale! FLAT 40% OFF on all styles. Shop now https://shop.example/x")))
        assertIs<Verdict.Unsure>(c.classify(sms("VM-SHOPAP-P", "Your new collection is waiting. Visit https://shop.example/x")))
    }

    @Test
    fun `promo that looks like money stays unsure`() {
        val v = c.classify(sms("VM-SHOPAP-P", "ADDED: Rs 111 credited to your wallet! Spend with code ABCD1111 and get FLAT 20% OFF"))
        assertIs<Verdict.Unsure>(v)
        assertEquals(Event.Promo, v.event)
    }

    @Test
    fun `bill with due date is sure and without asks`() {
        assertIs<Verdict.Sure>(c.classify(sms("AD-ACTGRP-S", "Dear Customer, renewal bill for Rs. 111.00 is due by 15/01/2031. Pay at example.in")))
        assertIs<Verdict.Unsure>(c.classify(sms("AD-ACTGRP-S", "Dear Customer, renewal bill for Rs. 111.00 is ready")))
    }

    @Test
    fun `parcel on its way is sure but delivered asks`() {
        assertIs<Verdict.Sure>(c.classify(sms("AD-DLHVRY-S", "Your order is out for delivery. AWB 12345678901")))
        assertIs<Verdict.Unsure>(c.classify(sms("AD-DLHVRY-S", "Your order has been delivered. AWB 12345678901")))
    }

    @Test
    fun `bill without due date asks`() {
        assertIs<Verdict.Unsure>(c.classify(sms("AD-AIRTEL", "Your Airtel bill of Rs 799.00 is ready")))
    }

    @Test
    fun `hostile text stays fast and safe`() {
        val long = listOf(
            "OTP ".repeat(4000) + "1".repeat(3000),
            "Rs. 1 ".repeat(3000) + "debited from a/c " + "x".repeat(5000),
            "at ".repeat(5000) + "on 12-10-26 ".repeat(2000),
            "A/c XX1234 debited " + "1,2".repeat(4000),
            "".padEnd(20000, '-') + "to " + "VPA ".repeat(3000),
        )
        val t0 = System.nanoTime()
        long.forEach { c.classify(sms("AD-ACME-S", it)) }
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 3000)
    }

    @Test
    fun `classifier slot accepts other implementations`() {
        val always = Classifier { Verdict.Unsure(Event.Unknown, 0.1f) }
        assertIs<Verdict.Unsure>(always.classify(sms("X", "y")))
    }
}
