package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ConvoTest {
    private fun Convo.fill(n: Int) = repeat(n) { add(if (it % 2 == 0) Role.User else Role.Assistant, "t$it") }

    @Test
    fun `it keeps turns in order`() {
        val c = Convo()
        c.fill(3)
        assertEquals(listOf("t0", "t1", "t2"), c.turns.map { it.text })
    }

    @Test
    fun `it keeps the last twelve turns and starts on a user turn`() {
        val c = Convo()
        c.fill(13)
        assertEquals((2..12).map { "t$it" }, c.turns.map { it.text })
        c.fill(14)
        assertEquals(12, c.turns.size)
        assertEquals(Role.User, c.turns.first().role)
    }

    @Test
    fun `tool turns count and leading tool turns are dropped`() {
        val c = Convo(4)
        c.add(Role.User, "u1")
        c.add(Role.Assistant, "a1")
        c.add(Role.Tool, "r1")
        c.add(Role.Assistant, "a2")
        c.add(Role.User, "u2")
        assertEquals(listOf("u2"), c.turns.map { it.text })
    }

    @Test
    fun `a window without user turns is empty and clear resets`() {
        val c = Convo()
        c.add(Role.Assistant, "a")
        assertEquals(emptyList(), c.turns)
        c.add(Role.User, "u")
        c.clear()
        assertEquals(emptyList(), c.turns)
    }
}
