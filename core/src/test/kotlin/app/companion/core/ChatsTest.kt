package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatsTest {
    private val none = emptySet<String>()
    private val rules = RulesClassifier()
    private fun wa(sender: String, body: String, title: String = "") = Raw(Source.Wa, sender, title, body, 1_700_000_000_000)
    private fun routed(sender: String, body: String, title: String = "", vips: Set<String> = none) = Chats.route(wa(sender, body, title), vips)

    @Test
    fun `known brands resolve from a chat name`() {
        listOf("Swiggy", "HDFC Bank", "IndiGo", "Shipway", "Amazon", "Amazon.in", "IndiGo Customer Care", "swiggy official").forEach {
            assertNotNull(Chats.brand(it), it)
        }
        assertEquals("Amazon", Chats.brand("Amazon.in")?.name)
        assertEquals("IndiGo", Chats.brand("IndiGo Customer Care")?.name)
    }

    @Test
    fun `people are not brands`() {
        listOf("Ravi", "Ola Bini", "Mom", "Swiggy Rahul", "Vi", "", "Group Chat").forEach { assertNull(Chats.brand(it), it) }
    }

    @Test
    fun `every brand name round trips`() {
        Brands.db.brands.filter { it.name.length >= 3 }.forEach { assertEquals(it.name, Chats.brand(it.name)?.name, it.name) }
    }

    @Test
    fun `brand chats take the sender of the brand`() {
        val r = routed("Swiggy Customer Care", "Your order is out for delivery")
        assertEquals("Swiggy", r.sender)
        assertEquals(Source.Wa, r.source)
        assertEquals("", r.title)
        assertEquals("Swiggy", routed("Rahul", "hi", title = "Swiggy").sender)
    }

    @Test
    fun `people and important contacts are left alone`() {
        assertEquals("Ravi", routed("Ravi", "lunch?").sender)
        assertEquals("Ola", routed("Ola", "ok", vips = setOf("ola")).sender)
        val sms = Raw(Source.Sms, "Swiggy", "", "hi", 1)
        assertEquals(sms, Chats.route(sms, none))
    }

    @Test
    fun `brand chats run through the same extraction`() {
        val d = rules.classify(routed("Swiggy", "Your order #123 is out for delivery. Track it in the app.")).event
        assertTrue(d is Event.Delivery && d.merchant == "Swiggy" && d.stage == Stage.Out, "$d")
        val o = rules.classify(routed("HDFC Bank", "482913 is your OTP for login. Do not share it.")).event
        assertTrue(o is Event.Otp && o.code == "482913", "$o")
        val m = rules.classify(routed("HDFC Bank", "Rs 1,250.00 debited from a/c XX4021 on 03-10-25 to VPA swiggy@icici. Ref 123456789012")).event
        assertTrue(m is Event.Debit && m.paise == 125_000L && m.last4 == "4021", "$m")
        val b = rules.classify(routed("Shipway", "Your parcel has been shipped, AWB 998877. Track your shipment.")).event
        assertTrue(b is Event.Delivery, "$b")
    }

    @Test
    fun `unrecognised chats stay personal`() {
        assertEquals(Event.Personal, rules.classify(wa("Ravi", "paid Rs 500 for the cab yesterday")).event)
        assertEquals(Event.Personal, rules.classify(routed("Swiggy", "Thanks for chatting with us")).event)
    }

    @Test
    fun `quiet chats are only unimportant chat messages`() {
        val chat = wa("Group", "lol")
        assertTrue(Chats.quiet(Event.Personal, chat, none))
        assertFalse(Chats.quiet(Event.Personal, chat, setOf("group")))
        assertFalse(Chats.quiet(Event.Personal, wa("Ravi", "are you free"), none))
        assertFalse(Chats.quiet(Event.Personal, Raw(Source.Sms, "+919800000000", "", "lol", 1), none))
        assertFalse(Chats.quiet(Event.Alert, chat, none))
    }

    @Test
    fun `important list gains a name once`() {
        assertEquals("Ravi", Chats.vip("", "Ravi"))
        assertEquals("Mom\nRavi", Chats.vip("Mom\n", " Ravi "))
        assertEquals("Mom\nRavi", Chats.vip("Mom\nRavi", "ravi"))
        assertEquals("Mom", Chats.vip("Mom", "  "))
    }
}
