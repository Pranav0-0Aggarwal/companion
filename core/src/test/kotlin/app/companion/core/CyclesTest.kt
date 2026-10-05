package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CyclesTest {
    private val day = 86_400_000L

    private fun slip(d: Long, due: Long? = null, last4: String? = "1234", name: String? = null) = Slip(d * day, due, last4, name)

    private fun groups(vararg s: Slip) = Cycles.of(s.toList()) { it }

    @Test
    fun `same card within the window is one cycle and the newest message leads`() {
        val stmt = slip(100, due = 20_000)
        val note = slip(112, due = 20_005)
        val g = groups(stmt, note)
        assertEquals(1, g.size)
        assertEquals(listOf(note, stmt), g.single())
    }

    @Test
    fun `due dates 12 days apart are two cycles`() {
        assertEquals(2, groups(slip(100, due = 20_000), slip(105, due = 20_012)).size)
        assertEquals(1, groups(slip(100, due = 20_000), slip(105, due = 20_010)).size)
    }

    @Test
    fun `without a due date messages 25 days apart or less chain together`() {
        assertEquals(1, groups(slip(100), slip(120)).size)
        assertEquals(2, groups(slip(100), slip(130)).size)
        assertEquals(1, groups(slip(100, due = 20_000), slip(110)).size)
    }

    @Test
    fun `different cards stay separate`() {
        assertEquals(2, groups(slip(100, last4 = "1111"), slip(101, last4 = "2222")).size)
    }

    @Test
    fun `without a last4 the name is compared ignoring case and spaces`() {
        assertEquals(1, groups(slip(100, last4 = null, name = "HDFC Bank"), slip(101, last4 = null, name = "hdfcbank")).size)
        assertEquals(2, groups(slip(100, last4 = null, name = "HDFC Bank"), slip(101, last4 = null, name = "Axis Bank")).size)
    }

    @Test
    fun `messages with no account never group`() {
        assertEquals(2, groups(slip(100, last4 = null), slip(101, last4 = null)).size)
    }

    @Test
    fun `a chain keeps growing as each message is near the last`() {
        assertEquals(1, groups(slip(100), slip(120), slip(140)).size)
    }

    @Test
    fun `cycles keep the order of their newest message in the input`() {
        val a = slip(130, last4 = "1111")
        val b = slip(100, last4 = "2222")
        val c = slip(115, last4 = "1111")
        assertEquals(listOf(a, b), groups(a, b, c).map { it.first() })
    }
}
