package app.companion.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MerchantTest {
    private fun cleaned(s: String) = Merchant.clean(s)

    @Test
    fun `strips quotes and edge punctuation`() {
        assertEquals("Swiggy", cleaned("'Swiggy"))
        assertEquals("Swiggy", cleaned("\"Swiggy\""))
        assertEquals("Swiggy", cleaned("`Swiggy`"))
        assertEquals("Amazon Pay", cleaned("*AMAZON*PAY*"))
        assertEquals("Rahul Store", cleaned("  - Rahul   Store . "))
        assertEquals("Domino's", cleaned("DOMINO’S"))
    }

    @Test
    fun `drops legal and location suffixes`() {
        assertEquals("Acme Labs", cleaned("ACME LABS PRIVATE LIMITED"))
        assertEquals("Acme Labs", cleaned("Acme Labs Pvt. Ltd."))
        assertEquals("Acme", cleaned("Acme Technologies India Pvt Ltd"))
        assertEquals("Acme Cafe", cleaned("Acme Cafe Bangalore"))
        assertEquals("Acme Cafe", cleaned("Acme Cafe (Bengaluru)"))
        assertEquals("Acme", cleaned("Acme IN"))
        assertEquals("Acme", cleaned("Acme Payments"))
        assertEquals("Bank Of India", cleaned("Bank of India"))
        assertEquals("Ltd", cleaned("Ltd"))
    }

    @Test
    fun `drops upi handles prefixes and numeric refs`() {
        assertEquals("Rahul", cleaned("rahul@okicici"))
        assertEquals("Swiggy", cleaned("swiggy@icici"))
        assertEquals("Rahul Kumar", cleaned("UPI-RAHUL KUMAR"))
        assertEquals("Rahul Kumar", cleaned("upi/rahul kumar"))
        assertEquals("Rahul Kumar", cleaned("UPI-RAHUL KUMAR-123456789012"))
        assertEquals("Acme", cleaned("Acme 8826371"))
        assertEquals("Acme", cleaned("Acme TXN9X8Y7Z6"))
        assertEquals("Store 24", cleaned("Store 24"))
    }

    @Test
    fun `title cases except acronyms`() {
        assertEquals("Rahul Kumar", cleaned("RAHUL KUMAR"))
        assertEquals("Rahul Kumar", cleaned("rahul kumar"))
        assertEquals("BigBasket", cleaned("BigBasket"))
        assertEquals("HDFC Mutual Fund", cleaned("hdfc MUTUAL FUND"))
        listOf("HDFC", "ICICI", "SBI", "IRCTC", "KFC", "OYO", "CRED", "IKEA", "BSNL", "LIC", "IDFC", "RBL", "AU", "HSBC", "DTDC", "BMS").forEach {
            assertEquals("$it Stores", cleaned("$it STORES"))
        }
    }

    @Test
    fun `resolves to brands`() {
        assertEquals("Swiggy", Merchant.resolve("'Swiggy"))
        assertEquals("Swiggy", Merchant.resolve("swiggy@icici"))
        assertEquals("Swiggy", Merchant.resolve("SWIGGY"))
        assertEquals("Swiggy", Merchant.resolve("Swiggy Food"))
        assertEquals("Swiggy", Merchant.resolve("BUNDL TECHNOLOGIES PVT LTD"))
        assertEquals("Swiggy Instamart", Merchant.resolve("SWIGGY INSTAMART"))
        assertEquals("Swiggy Dineout", Merchant.resolve("swiggy dineout"))
        assertEquals("Rahul Kumar", Merchant.resolve("RAHUL KUMAR"))
        assertNull(Merchant.resolve("  ''  "))
        assertNull(Merchant.resolve(null))
    }

    @Test
    fun `brand matching`() {
        assertEquals("Amazon", Merchant.brand("amazon")?.name)
        assertEquals("Amazon Pay", Merchant.brand("Amazon Pay India")?.name)
        assertEquals("Amazon Prime", Merchant.brand("Amazon Prime Membership")?.name)
        assertEquals("Amazon", Merchant.brand("Amazon Seller Services")?.name)
        assertEquals("Zomato", Merchant.brand("Zomato Online Order")?.name)
        assertEquals("Blinkit", Merchant.brand("Blinkit Store 12")?.name)
        assertNotEquals(Merchant.brand("Zomato")?.name, Merchant.brand("Blinkit")?.name)
        assertEquals("Tata 1mg", Merchant.brand("Tata 1mg")?.name)
        assertEquals("Swiggy Instamart", Merchant.brand("Swiggy Instamart Order")?.name)
        assertEquals("Swiggy", Merchant.brand("Swiggy Order 77")?.name)
        assertEquals("KFC", Merchant.brand("kfc")?.name)
        assertNull(Merchant.brand("KFC Koramangala"))
        assertNull(Merchant.brand("Ola Electric Charging"))
        assertNull(Merchant.brand("Rahul Kumar"))
        assertNull(Merchant.brand("Swiggyish"))
    }

    @Test
    fun `sender and body lookup`() {
        val db = Brands.db
        assertEquals("Swiggy", db.stem("SWIGGY")?.name)
        assertEquals("HDFC Bank", db.stem("HDFCBK")?.name)
        assertEquals("SBI Card", db.stem("SBICRD")?.name)
        assertEquals("SBI", db.stem("SBIINB")?.name)
        assertNull(db.stem("CREDIT"))
        assertEquals("Amazon Pay", db.find("paid via Amazon Pay UPI", 3)?.name)
        assertEquals("Amazon", db.find("order from AMAZON shipped", 3)?.name)
        assertEquals("Swiggy Instamart", db.find("Your Swiggy Instamart order", 3)?.name)
        assertNull(db.find("hello amazons", 3))
    }

    @Test
    fun `brands in events`() {
        assertEquals("GPay", Brands.shop("GPAY", ""))
        assertEquals("Netflix", Brands.service("VM-NETFLX-S", ""))
        assertEquals("HDFC Bank", Brands.bank("HDFCBK", ""))
        assertEquals("Axis Bank", Brands.bank("XX", "Your Axis Bank a/c"))
        assertNull(Brands.shop("HDFCBK", "debited at somewhere"))
    }

    @Test
    fun `auto alias guess`() {
        assertEquals("Swiggy", Merchant.guess("Acme Eats", "VM-SWIGGY-S"))
        assertNull(Merchant.guess("Swiggy", "VM-SWIGGY-S"))
        assertNull(Merchant.guess("Rahul Kumar", "HDFCBK"))
        assertNull(Merchant.guess("Rahul Kumar", "PAYTMB"))
        assertNull(Merchant.guess("Rahul Kumar", "XYZABC"))
        assertTrue(!Merchant.due(1))
        assertTrue(Merchant.due(2))
    }

    @Test
    fun `truncated and tagged upi payees resolve to the brand and its category`() {
        listOf("AMAZON PAY IN G", "Amazon Pay India Pri", "Amazon Pay In R", "amazon pay indi", "AMAZON PAY INDIA PRIVATE LIMITED").forEach { assertEquals("Amazon Pay", Merchant.resolve(it), it) }
        listOf("EATCLUB BRANDS", "eatclub@ybl", "Eatclub", "EAT CLUB", "EATCLUB BRANDS PRIV").forEach { assertEquals("EatClub", Merchant.resolve(it), it) }
        assertEquals(Category.Food, Category.of("EatClub"))
        assertEquals(Category.Food, Category.of(Merchant.resolve("eatclub@ybl")))
        assertEquals(Category.Food, Category.of(Event.Debit(25000, "INR", "1234", "HDFC Bank", "EatClub", Mode.Upi)))
        assertEquals(Category.Transfer, Category.of(Event.Debit(25000, "INR", "1234", "HDFC Bank", "Rahul Kumar", Mode.Upi)))
        assertEquals(Category.Shopping, Category.of(Merchant.resolve("AMAZON PAY IN G")))
    }

    @Test
    fun `keys are normalized`() {
        assertEquals("swiggy instamart", Merchant.key("Swiggy-Instamart"))
        assertEquals(Merchant.key("'Swiggy"), Merchant.key("swiggy"))
    }

    @Test
    fun `events carry the name`() {
        val d = Event.Debit(100, "INR", null, null, "Swiggy", Mode.Upi)
        assertEquals("Swiggy", d.who())
        assertEquals("My Swiggy", (d.named("My Swiggy") as Event.Debit).merchant)
        assertEquals("Biller", (Event.Bill(1, "INR", null, null, "A", null).named("Biller") as Event.Bill).biller)
        assertNull(Event.Alert.who())
    }
}

class BrandDbTest {
    private val assets = File("../app/src/main/assets")
    private val db = BrandDb.parse(File(assets, "brands.json").readText())

    @Suppress("UNCHECKED_CAST")
    private val slugs = (Json.obj(File(assets, "brand_icons.json").readText())["icons"] as List<Map<String, Any?>>).map { it["slug"] as String }.toSet()

    @Test
    fun `parses with valid fields`() {
        assertTrue(db.brands.size >= 150)
        val cats = Category.entries.map { it.label }.toSet()
        db.brands.forEach {
            assertTrue(it.cat in cats, "${it.name} cat ${it.cat}")
            assertTrue(Regex("#[0-9A-Fa-f]{6}").matches(it.color), "${it.name} color")
            assertTrue(it.aliases.isNotEmpty() && it.aliases.all { a -> a == a.lowercase() && a.isNotBlank() }, "${it.name} aliases")
        }
    }

    @Test
    fun `every icon exists and names and aliases are unique`() {
        db.brands.mapNotNull { it.icon }.forEach { assertTrue(it in slugs, "missing icon $it") }
        assertEquals(db.brands.size, db.brands.map { it.name }.toSet().size)
        val all = db.brands.flatMap { b -> b.aliases.map { it to b.name } }
        assertEquals(all.size, all.map { it.first }.toSet().size)
    }

    @Test
    fun `legacy names and bank list resolve`() {
        BankNames.forEach { assertNotNull(db.named(it)?.takeIf { b -> b.bank }, it) }
        listOf("Amazon Pay", "Amazon", "Flipkart", "Myntra", "Swiggy", "Zomato", "Blinkit", "Zepto", "BigBasket", "Uber", "Ola", "Rapido", "IRCTC", "MakeMyTrip", "Airtel", "Jio", "Netflix", "Spotify", "GPay", "PhonePe", "Paytm", "CRED", "INDmoney", "Google", "Delhivery", "Blue Dart", "DTDC", "Ekart", "IndiGo", "Vistara", "Air India")
            .forEach { assertNotNull(db.named(it), it) }
    }

    @Test
    fun `longest alias wins`() {
        assertEquals("Amazon Pay", db.find("amazon pay balance", 1)?.name)
        assertEquals("Amazon Prime", db.find("amazon prime video", 1)?.name)
        assertEquals("Swiggy Instamart", db.find("swiggy instamart", 1)?.name)
        assertEquals("Swiggy", db.find("swiggy", 1)?.name)
        assertEquals("Swiggy Dineout", db.find("swiggy dineout table", 1)?.name)
    }
}

class SenderTitleTest {
    @Test
    fun `dlt headers resolve to brands`() {
        mapOf(
            "JM-ENFILD-S" to "Royal Enfield", "AD-ACTGRP-S" to "ACT Fibernet", "VA-ACTGRP-S" to "ACT Fibernet", "AX-BPCLIN-S" to "Bharat Petroleum",
            "AX-HDFCBK-S" to "HDFC Bank", "VM-ICICIT-S" to "ICICI Bank", "JD-ICICIO-S" to "ICICI Bank", "VK-SBIINB-S" to "SBI", "AD-SBICRD-S" to "SBI Card",
            "AX-AXISBK-S" to "Axis Bank", "VM-KOTAKB-S" to "Kotak Bank", "JX-JIOINF-S" to "Jio", "JD-JIOCOM-S" to "Jio", "AD-AIRTEL-S" to "Airtel",
            "VM-ARTLTD-S" to "Airtel", "AX-AMAZON-S" to "Amazon", "VM-AMZNIN-S" to "Amazon", "AD-FLPKRT-S" to "Flipkart", "VM-SWIGGY-S" to "Swiggy",
            "VM-SWIGGI-S" to "Swiggy", "AX-ZOMATO-S" to "Zomato", "JM-PAYTMB-S" to "Paytm", "AX-PHONPE-S" to "PhonePe", "AD-CREDIN-S" to "CRED",
            "VM-CREDCL-S" to "CRED", "VM-IRCTCS-S" to "IRCTC", "AX-MYNTRA-S" to "Myntra", "AD-BLINKT-S" to "Blinkit", "VM-ZEPTON-S" to "Zepto",
            "AX-UBERIN-S" to "Uber", "VM-OLACAB-S" to "Ola", "AD-ETCLUB-S" to "EatClub",
        ).forEach { (k, v) -> assertEquals(v, Brands.header(k), k) }
    }

    @Test
    fun `unknown stems fall back to the stem in capitals and other senders are left alone`() {
        assertEquals("SLCBNK", Brands.header("AX-SLCBNK-S"))
        assertEquals("QWERTY", Brands.header("VM-QWERTY"))
        assertEquals("Swiggy", Brands.header("SWIGGY"))
        assertNull(Brands.header("Rahul Kumar"))
        assertNull(Brands.header("+919876543210"))
        assertNull(Brands.header("Amit"))
        assertEquals("Rahul", Senders.title("Rahul"))
        assertEquals("HDFC Bank", Senders.title("AX-HDFCBK-S"))
    }
}
