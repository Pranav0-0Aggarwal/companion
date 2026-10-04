package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VerbatimTest {
    private fun ok(f: Field, v: String, t: String) = Verbatim.ok(f, v, t)

    @Test
    fun `an otp must be a standalone digit token`() {
        assertTrue(ok(Field.OtpCode, "123456", "Your code is 123456."))
        assertTrue(ok(Field.OtpCode, " 4821 ", "OTP:4821 valid 5 min"))
        assertTrue(ok(Field.OtpCode, "123456", "G-123456 is your code"))
        assertTrue(ok(Field.OtpCode, "1234", "code 1234, do not share"))
    }

    @Test
    fun `otp digits inside a longer number are rejected`() {
        assertFalse(ok(Field.OtpCode, "1234", "ref 123456"))
        assertFalse(ok(Field.OtpCode, "3456", "ref 123456"))
        assertFalse(ok(Field.OtpCode, "1234", "ref 12345678901"))
        assertFalse(ok(Field.OtpCode, "1234", "call 9876543210"))
        assertFalse(ok(Field.OtpCode, "1234", "Rs.1234.50 paid"))
        assertFalse(ok(Field.OtpCode, "1234", "Rs.1,234 paid"))
        assertFalse(ok(Field.OtpCode, "1234", "id A1234"))
        assertFalse(ok(Field.OtpCode, "1234", "id 1234B"))
        assertFalse(ok(Field.OtpCode, "1234", "ref 77-1234"))
        assertFalse(ok(Field.OtpCode, "1234", "ref 1234-77"))
    }

    @Test
    fun `an otp must be four to eight plain digits that appear in the text`() {
        assertFalse(ok(Field.OtpCode, "123", "code 123"))
        assertFalse(ok(Field.OtpCode, "123456789", "code 123456789"))
        assertFalse(ok(Field.OtpCode, "12a456", "code 12a456"))
        assertFalse(ok(Field.OtpCode, "123456", "code 123 456"))
        assertFalse(ok(Field.OtpCode, "123456", "code 123-456"))
        assertFalse(ok(Field.OtpCode, "654321", "code 123456"))
        assertFalse(ok(Field.OtpCode, "", "code 123456"))
    }

    @Test
    fun `an amount equals a number in the text without commas or currency`() {
        val t = "Rs.1,23,456.00 debited from A/c XX1234"
        assertTrue(ok(Field.Amount, "123456", t))
        assertTrue(ok(Field.Amount, "123456.00", t))
        assertTrue(ok(Field.Amount, "1,23,456", t))
        assertTrue(ok(Field.Amount, "Rs.1,23,456.00", t))
        assertTrue(ok(Field.Amount, "₹ 123456", t))
        assertTrue(ok(Field.Amount, "INR 123456.0", t))
        assertTrue(ok(Field.Amount, "500.5", "paid Rs 500.50"))
        assertTrue(ok(Field.Amount, "500", "paid Rs500 today"))
        assertTrue(ok(Field.Amount, "750", "paid 750 INR"))
    }

    @Test
    fun `an amount cannot be taken from a mask or a reference`() {
        assertFalse(ok(Field.Amount, "1234", "Rs 500 debited from A/c XX1234"))
        assertFalse(ok(Field.Amount, "123456", "Rs 500 paid, ref123456"))
        assertTrue(ok(Field.Amount, "1234", "Rs 500 debited from A/c XX1234, avl bal 1,234"))
    }

    @Test
    fun `an amount that only matches part of a number is rejected`() {
        val t = "Rs.1,23,456.00 debited from A/c XX1234"
        assertFalse(ok(Field.Amount, "12345", t))
        assertFalse(ok(Field.Amount, "23456", t))
        assertFalse(ok(Field.Amount, "1234567", t))
        assertFalse(ok(Field.Amount, "1234", t))
        assertFalse(ok(Field.Amount, "123456.50", t))
        assertFalse(ok(Field.Amount, "500", "paid Rs 500.50"))
    }

    @Test
    fun `an amount must be a positive plain number`() {
        assertFalse(ok(Field.Amount, "0", "paid Rs 0"))
        assertFalse(ok(Field.Amount, "-500", "paid Rs 500"))
        assertFalse(ok(Field.Amount, "five hundred", "paid five hundred"))
        assertFalse(ok(Field.Amount, "", "paid Rs 500"))
        assertFalse(ok(Field.Amount, "1e3", "paid 1e3"))
    }

    @Test
    fun `paise comes from the same normalised number`() {
        assertEquals(12345600L, Verbatim.paise("Rs.1,23,456.00"))
        assertEquals(50L, Verbatim.paise("0.5"))
        assertNull(Verbatim.paise("10.005"))
        assertNull(Verbatim.paise("0"))
        assertNull(Verbatim.paise("abc"))
    }

    @Test
    fun `a merchant is a case insensitive substring`() {
        assertTrue(ok(Field.Merchant, "Zed Mart", "Paid to ZED MART on 04-10"))
        assertTrue(ok(Field.Merchant, "zed mart", "Paid to Zed\nMart"))
        assertTrue(ok(Field.Merchant, "  Zed Mart ", "Paid to Zed Mart."))
        assertFalse(ok(Field.Merchant, "Zed Marts", "Paid to Zed Mart"))
        assertFalse(ok(Field.Merchant, "Other Shop", "Paid to Zed Mart"))
        assertFalse(ok(Field.Merchant, "", "Paid to Zed Mart"))
        assertFalse(ok(Field.Merchant, "Z", "Paid to Zed Mart"))
        assertFalse(ok(Field.Merchant, "12", "Ref 1234"))
    }

    @Test
    fun `a due date must appear in the text and be a real date`() {
        assertTrue(ok(Field.Due, "20 Oct 2026", "Pay by 20 Oct 2026 to avoid fees"))
        assertTrue(ok(Field.Due, "20/10/2026", "Due date: 20/10/2026"))
        assertTrue(ok(Field.Due, "20 OCT 2026", "pay by 20 oct 2026"))
        assertFalse(ok(Field.Due, "21 Oct 2026", "Pay by 20 Oct 2026"))
        assertFalse(ok(Field.Due, "31/02/2026", "Pay by 31/02/2026"))
        assertFalse(ok(Field.Due, "next friday", "Pay by next friday"))
        assertFalse(ok(Field.Due, "", "Pay by 20 Oct 2026"))
    }
}
