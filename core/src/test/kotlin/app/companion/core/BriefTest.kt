package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BriefTest {
    @Test
    fun `only the lines that exist are joined`() {
        val f = Brief.face("Asha", listOf(Brief.meeting("11:00"), Brief.bill("HDFC bill", "₹18,240", 3, "Fri"), Brief.orders("Swiggy", 1)))!!
        assertEquals("Morning, Asha", f.title)
        assertEquals("First meeting 11:00 · HDFC bill ₹18,240 due Fri · Swiggy order arriving", f.text)
        assertEquals("Good morning", Brief.face("", listOf("x"))!!.title)
        assertNull(Brief.face("Asha", emptyList()))
    }

    @Test
    fun `bills read naturally by distance`() {
        assertEquals("Water bill due today", Brief.bill("Water bill", null, 0, "Mon"))
        assertEquals("Water bill ₹90 due tomorrow", Brief.bill("Water bill", "₹90", 1, "Mon"))
        assertEquals("Water bill due Mon", Brief.bill("Water bill", null, 2, "Mon"))
        assertEquals("1 bill due today", Brief.due(1))
        assertEquals("2 bills due today", Brief.due(2))
        assertEquals("Order arriving", Brief.orders(null, 1))
        assertEquals("2 orders arriving", Brief.orders("Swiggy", 2))
    }

    @Test
    fun `the redacted faces carry no details`() {
        listOf(Hidden.meet, Hidden.brief).forEach { f -> assertFalse(Regex("[₹0-9]").containsMatchIn(f.title + f.text.orEmpty())) }
    }

    @Test
    fun `the cover brief shows only in the morning window and only with something to say`() {
        assertTrue(Cover.brief(6 * 60, true, 0))
        assertTrue(Cover.brief(10 * 60 + 30, false, 2))
        assertFalse(Cover.brief(6 * 60 - 1, true, 1))
        assertFalse(Cover.brief(10 * 60 + 31, true, 1))
        assertFalse(Cover.brief(8 * 60, false, 0))
    }

    @Test
    fun `a meeting within the hour outranks bills but not codes`() {
        assertTrue(Cover.meet(0))
        assertTrue(Cover.meet(60))
        assertFalse(Cover.meet(61))
        assertFalse(Cover.meet(null))
        assertEquals(listOf(Lane.Out, Lane.Code, Lane.Meet, Lane.Due, Lane.Spent), Cover.lanes(true, true, 3, 2, meet = true))
        assertEquals(listOf(Lane.Meet, Lane.Due), Cover.lanes(false, false, 3, 0, meet = true))
    }

    @Test
    fun `the brief yields to anything more urgent and leads bills and spend`() {
        assertEquals(listOf(Lane.Brief, Lane.Due, Lane.Spent), Cover.lanes(false, false, 0, 2, brief = true))
        assertEquals(listOf(Lane.Brief), Cover.lanes(false, false, null, 0, brief = true))
        assertEquals(listOf(Lane.Meet, Lane.Due), Cover.lanes(false, false, 0, 0, meet = true, brief = true))
        assertEquals(listOf(Lane.Code, Lane.Due), Cover.lanes(false, true, 0, 0, brief = true))
        assertEquals(listOf(Lane.Out), Cover.lanes(true, false, null, 0, brief = true))
    }

    @Test
    fun `defaults`() {
        assertEquals(480, BriefOpts().at)
        assertTrue(BriefOpts().on)
        assertFalse(MeetOpts().on)
        assertEquals(listOf(5, 10, 15, 30), Meets.leads)
    }
}
