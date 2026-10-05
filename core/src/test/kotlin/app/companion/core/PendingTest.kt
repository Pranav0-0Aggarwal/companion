package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PendingTest {
    private val pay = Do.Pay(7)

    @Test
    fun `refs round trip and reject junk`() {
        assertEquals("i:123", Ref.item(123))
        assertEquals(123L, Ref.id("i:123"))
        assertEquals(123L, Ref.id(" I:123 "))
        assertEquals(123L, Ref.id("123"))
        assertEquals(9L, Ref.id("i9"))
        assertNull(Ref.id("i:0"))
        assertNull(Ref.id("HDFC"))
        assertNull(Ref.id("i:12x"))
        assertNull(Ref.id(null))
        assertNull(Ref.id("i:1234567890123"))
    }

    @Test
    fun `a pending action confirms once then runs to done or fail`() {
        val p = Pending(1, pay, "Mark paid")
        assertEquals(Pend.Wait, p.at)
        val run = assertNotNull(p.confirm())
        assertEquals(Pend.Run, run.at)
        assertNull(run.confirm())
        assertNull(run.cancel())
        assertEquals(Pend.Done, run.finish(true)?.at)
        assertEquals(Pend.Fail, run.finish(false)?.at)
        assertNull(run.finish(true)?.finish(true))
    }

    @Test
    fun `cancel only works while waiting`() {
        val p = Pending(1, pay, "Mark paid")
        val c = assertNotNull(p.cancel())
        assertEquals(Pend.Cancel, c.at)
        assertNull(c.confirm())
        assertNull(c.cancel())
        assertNull(p.finish(true))
    }

    @Test
    fun `held store hands out keys and confirms by key`() {
        val h = Held()
        val (a, fresh) = h.add(pay, "Mark paid")
        assertTrue(fresh)
        assertEquals(Pend.Run, h.confirm(a.key)?.at)
        assertNull(h.confirm(a.key))
        assertNull(h.cancel(a.key))
        assertEquals(Pend.Done, h.finish(a.key, true)?.at)
        assertEquals(Pend.Done, h.get(a.key)?.at)
        assertNull(h.confirm(99))
    }

    @Test
    fun `the same waiting action is not queued twice`() {
        val h = Held()
        val (a, x) = h.add(pay, "Mark paid")
        val (b, y) = h.add(pay, "Mark paid")
        assertTrue(x)
        assertFalse(y)
        assertEquals(a.key, b.key)
        h.cancel(a.key)
        val (c, z) = h.add(pay, "Mark paid")
        assertTrue(z)
        assertTrue(c.key != a.key)
    }

    @Test
    fun `held store keeps only the latest and clears`() {
        val h = Held(2)
        val k = (1L..3L).map { h.add(Do.Dismiss(it), "d$it").first.key }
        assertNull(h.get(k[0]))
        assertNotNull(h.get(k[1]))
        assertNotNull(h.get(k[2]))
        assertNull(h.confirm(k[0]))
        h.clear()
        assertNull(h.get(k[2]))
    }

    @Test
    fun `different targets are different actions`() {
        val h = Held()
        assertTrue(h.add(Do.File(1, "food"), "a").second)
        assertTrue(h.add(Do.File(1, "bills"), "b").second)
        assertTrue(h.add(Do.File(2, "food"), "c").second)
        assertFalse(h.add(Do.File(2, "food"), "c").second)
    }
}
