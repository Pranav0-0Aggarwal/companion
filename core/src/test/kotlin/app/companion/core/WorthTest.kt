package app.companion.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorthTest {
    private val none = emptySet<String>()

    @Test
    fun `questions money and plans are kept`() {
        assertTrue(Worth.dm("Ravi", "Are you free", none))
        assertTrue(Worth.dm("Ravi", "can you send ₹500", none))
        assertTrue(Worth.dm("Ravi", "Dinner at 8 pm", none))
        assertTrue(Worth.dm("Ravi", "see you tomorrow", none))
        assertTrue(Worth.dm("Ravi", "Rs 300 for the cab", none))
    }

    @Test
    fun `chatter becomes a count`() {
        assertFalse(Worth.dm("Group", "lol", none))
        assertFalse(Worth.dm("Group", "good morning everyone", none))
        assertFalse(Worth.dm("Group", "Nice photo", none))
    }

    @Test
    fun `important contacts always kept`() {
        assertTrue(Worth.dm("Mom", "ok", setOf("mom")))
    }
}
