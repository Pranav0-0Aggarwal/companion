package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals

class LinkGattRuleTest {
    @Test
    fun `no session and enable accepts`() = assertEquals(Cccd.Accept, LinkGattRule.cccd(null, "a", true))

    @Test
    fun `same device enabling again restarts`() = assertEquals(Cccd.Restart, LinkGattRule.cccd("a", "a", true))

    @Test
    fun `other device enabling is rejected`() = assertEquals(Cccd.Reject, LinkGattRule.cccd("a", "b", true))

    @Test
    fun `active device disabling drops`() = assertEquals(Cccd.Drop, LinkGattRule.cccd("a", "a", false))

    @Test
    fun `disable from anyone else changes nothing`() {
        assertEquals(Cccd.Ignore, LinkGattRule.cccd("a", "b", false))
        assertEquals(Cccd.Ignore, LinkGattRule.cccd(null, "a", false))
    }
}
