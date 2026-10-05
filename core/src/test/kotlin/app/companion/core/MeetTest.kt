package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeetTest {
    private fun m(start: Long, end: Long, title: String = "t", place: String? = null, join: Join? = null) =
        Meeting(1, title, start, end, place, join, 1)

    @Test
    fun `google meet links are found with or without a scheme`() {
        assertEquals(Join(Via.Meet, "https://meet.google.com/abc-defg-hij"), Meets.join("https://meet.google.com/abc-defg-hij"))
        assertEquals(Join(Via.Meet, "https://meet.google.com/abc-defg-hij"), Meets.join("Join: meet.google.com/abc-defg-hij."))
        assertEquals("https://meet.google.com/abc-defg-hij?authuser=1", Meets.join("see http://meet.google.com/abc-defg-hij?authuser=1 now")?.url)
        assertNull(Meets.join("https://meet.google.com/lookup/abc"))
        assertNull(Meets.join("meet.google.com/abcd-defg-hij"))
    }

    @Test
    fun `zoom links include regional subdomains and personal rooms`() {
        assertEquals(Join(Via.Zoom, "https://zoom.us/j/123456789?pwd=Ab1"), Meets.join("https://zoom.us/j/123456789?pwd=Ab1"))
        assertEquals(Via.Zoom, Meets.join("https://us02web.zoom.us/j/98765432100")?.via)
        assertEquals(Via.Zoom, Meets.join("https://acme.zoom.us/my/jane.doe")?.via)
        assertNull(Meets.join("https://zoom.us/signin"))
    }

    @Test
    fun `teams and webex links`() {
        val t = "https://teams.microsoft.com/l/meetup-join/19%3ameeting_abc%40thread.v2/0?context=%7b%22Tid%22%7d"
        assertEquals(Join(Via.Teams, t), Meets.join("<$t>"))
        assertEquals(Via.Teams, Meets.join("https://teams.live.com/meet/9876543210?p=abc")?.via)
        assertEquals(Via.Webex, Meets.join("https://acme.webex.com/meet/jane")?.via)
        assertEquals(Via.Webex, Meets.join("https://acme.webex.com/acme/j.php?MTID=m123")?.via)
        assertNull(Meets.join("https://teams.microsoft.com/l/channel/1"))
    }

    @Test
    fun `lookalike hosts and embedded paths never match`() {
        assertNull(Meets.join("https://notzoom.us/j/123456789"))
        assertNull(Meets.join("https://zoom.us.evil.com/j/123456789"))
        assertNull(Meets.join("https://evil.com/?u=zoom.us/j/123456789"))
        assertNull(Meets.join("https://zoom.us@evil.com/j/123456789"))
        assertNull(Meets.join("https://evilmeet.google.com/abc-defg-hij"))
    }

    @Test
    fun `html descriptions are cleaned and the location wins`() {
        val html = "<a href=\"https://zoom.us/j/111222333?pwd=a&amp;x=1\">Join</a><br>Meet: <b>meet.google.com/abc-defg-hij</b>"
        assertEquals(Join(Via.Zoom, "https://zoom.us/j/111222333?pwd=a&x=1"), Meets.join(null, html))
        assertEquals(Via.Meet, Meets.join("https://meet.google.com/abc-defg-hij", html)?.via)
        assertNull(Meets.join(null, "Bring the report"))
        assertNull(Meets.join())
    }

    @Test
    fun `a physical place is any location that is not a link or a placeholder`() {
        assertEquals("Cafe Coffee Day, MG Road", Meets.place(" Cafe Coffee Day,  MG Road "))
        assertEquals("Room 4", Meets.place("Room 4 https://zoom.us/j/123456789"))
        assertNull(Meets.place(null))
        assertNull(Meets.place("  "))
        assertNull(Meets.place("https://zoom.us/j/123456789"))
        assertNull(Meets.place("meet.google.com/abc-defg-hij"))
        assertNull(Meets.place("www.example.com/room"))
        assertNull(Meets.place("Microsoft Teams Meeting"))
        assertNull(Meets.place("Zoom"))
    }

    @Test
    fun `lead times follow the kind of meeting`() {
        val o = MeetOpts(video = 5, place = 15)
        val link = Join(Via.Zoom, "https://zoom.us/j/1")
        assertEquals(5, Meets.lead(m(0, 1, join = link), o))
        assertEquals(5, Meets.lead(m(0, 1, place = "Room 4", join = link), o))
        assertEquals(15, Meets.lead(m(0, 1, place = "Room 4"), o))
        assertEquals(30, Meets.lead(m(0, 1, place = "Room 4"), o.copy(place = 30)))
        assertEquals(5, Meets.lead(m(0, 1), o))
    }

    @Test
    fun `only meetings with a link or a place when asked`() {
        val o = MeetOpts(only = true)
        assertFalse(Meets.wanted(m(0, 1), o))
        assertTrue(Meets.wanted(m(0, 1, place = "Room 4"), o))
        assertTrue(Meets.wanted(m(0, 1, join = Join(Via.Meet, "https://meet.google.com/abc-defg-hij")), o))
        assertTrue(Meets.wanted(m(0, 1), MeetOpts()))
        assertEquals("Meet", Meets.source(m(0, 1, place = "Room 4", join = Join(Via.Meet, "u"))))
        assertEquals("Room 4", Meets.source(m(0, 1, place = "Room 4")))
        assertNull(Meets.source(m(0, 1)))
    }

    @Test
    fun `a calendar reminder at an equal or closer lead covers the heads up`() {
        assertTrue(Meets.covered(5, listOf(5)))
        assertTrue(Meets.covered(15, listOf(10)))
        assertTrue(Meets.covered(5, listOf(30, 1)))
        assertFalse(Meets.covered(5, listOf(10)))
        assertFalse(Meets.covered(5, listOf(30)))
        assertFalse(Meets.covered(5, listOf(-1)))
        assertFalse(Meets.covered(5, emptyList()))
    }

    @Test
    fun `the step follows the lead and the live window`() {
        val s = 100 * Meets.MIN
        assertEquals(MeetPhase.Wait, Meets.step(s, 5, s - 11 * Meets.MIN))
        assertEquals(MeetPhase.Live, Meets.step(s, 5, s - 7 * Meets.MIN))
        assertEquals(MeetPhase.Heads, Meets.step(s, 5, s - 5 * Meets.MIN))
        assertEquals(MeetPhase.Heads, Meets.step(s, 15, s - 15 * Meets.MIN))
        assertEquals(MeetPhase.Heads, Meets.step(s, 15, s - 8 * Meets.MIN))
        assertEquals(MeetPhase.Heads, Meets.step(s, 5, s + Meets.LINGER - 1))
        assertEquals(MeetPhase.Done, Meets.step(s, 5, s + Meets.LINGER))
        assertFalse(Meets.ongoing(s, s - 11 * Meets.MIN))
        assertTrue(Meets.ongoing(s, s - 10 * Meets.MIN))
    }

    @Test
    fun `relative time rounds up to the minute`() {
        val s = 1_000 * Meets.MIN
        assertEquals("in 25 min", Meets.until(s, s - 25 * Meets.MIN))
        assertEquals("in 25 min", Meets.until(s, s - 24 * Meets.MIN - 1))
        assertEquals("in 1 min", Meets.until(s, s - 1))
        assertEquals("now", Meets.until(s, s))
        assertEquals("now", Meets.until(s, s + Meets.MIN))
        assertEquals("in 2 h", Meets.until(s, s - 120 * Meets.MIN))
        assertEquals("in 1 h 5 min", Meets.until(s, s - 65 * Meets.MIN))
        assertEquals("in 8m", Meets.chip(8))
        assertEquals("now", Meets.chip(0))
    }

    @Test
    fun `overlapping meetings clash and touching ones do not`() {
        val h = 60 * Meets.MIN
        val a = m(9 * h, 10 * h, "a")
        val b = m(10 * h, 11 * h, "b")
        val c = m(10 * h + 30 * Meets.MIN, 12 * h, "c")
        assertTrue(Meets.clashes(listOf(a, b)).isEmpty())
        val x = Meets.clashes(listOf(c, a, b)).single()
        assertEquals(listOf("b", "c"), listOf(x.a.title, x.b.title))
        assertEquals(10 * h + 30 * Meets.MIN, x.at)
        assertEquals(11 * h, x.until)
        assertEquals("Two meetings overlap at 10:30", Meets.clash(x) { "10:30" })
    }

    @Test
    fun `a nested meeting and chained overlaps are all reported`() {
        val h = 60 * Meets.MIN
        val long = m(9 * h, 12 * h, "long")
        val one = m(10 * h, 11 * h, "one")
        val two = m(10 * h + 30 * Meets.MIN, 11 * h + 30 * Meets.MIN, "two")
        assertEquals(3, Meets.clashes(listOf(long, one, two)).size)
    }

    @Test
    fun `the same event from two calendars is not a clash`() {
        val h = 60 * Meets.MIN
        assertTrue(Meets.clashes(listOf(m(h, 2 * h, "Standup"), m(h, 2 * h, "Standup").copy(cal = 2))).isEmpty())
    }
}
