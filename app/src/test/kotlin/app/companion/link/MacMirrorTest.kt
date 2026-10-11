package app.companion.link

import app.companion.core.BridgeWire
import app.companion.core.BulkCrypto
import app.companion.core.BulkFrame
import app.companion.core.BulkPipe
import app.companion.core.Json
import app.companion.core.LinkSession
import app.companion.link.bulk.Bulk
import app.companion.link.feature.MacMirror
import app.companion.link.powers.BridgeClient
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

class MacMirrorTest {
    private class Fake : BridgeClient(CoroutineScope(Dispatchers.Default)) {
        override val up: StateFlow<Boolean> = MutableStateFlow(true)
        val cmds = CopyOnWriteArrayList<Pair<String, Obj>>()
        val sent = CopyOnWriteArrayList<Pair<Int, List<Byte>>>()
        val first = CountDownLatch(1)

        override suspend fun cmd(t: String, p: Obj): Obj {
            cmds += t to p
            first.countDown()
            return mapOf("ok" to true)
        }

        override fun stream(type: Int, body: ByteArray) {
            sent += type to body.toList()
        }
    }

    @Test
    fun `registers the mirror handler on its own share key and stops twice`() {
        val n = LinkFeatures.all.size
        MacMirror.register { error("unused") }
        try {
            assertNotNull(Bulk.handlers["mirror"])
            assertEquals("mirror", MacMirror.key)
            assertEquals(true, MacMirror.cmds.isEmpty())
            MacMirror.stop()
            MacMirror.stop()
        } finally {
            LinkFeatures.all -= MacMirror
            Bulk.handlers -= "mirror"
        }
        assertEquals(n, LinkFeatures.all.size)
        assertNull(Bulk.handlers["mirror"])
    }

    private fun relay(fake: Fake, run: (BulkPipe, CountDownLatch) -> Unit) {
        val okm = ByteArray(64) { 3 }
        val token = ByteArray(16)
        MacMirror.register { fake }
        try {
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { srv ->
                val ps = Socket(InetAddress.getLoopbackAddress(), srv.localPort)
                val ms = srv.accept()
                ps.soTimeout = 5000
                ms.soTimeout = 5000
                val k = BulkCrypto.keys(okm, token, "mirror")
                val mac = BulkPipe(ms, BulkFrame(LinkSession(k.copyOfRange(0, 32), k.copyOfRange(32, 64), LinkSession.MAC_TO_PHONE, LinkSession.PHONE_TO_MAC, aad = BulkCrypto.AAD)), "mirror", null)
                val phone = BulkPipe(ps, BulkFrame(BulkCrypto.phone(okm, token, "mirror")), "mirror", null)
                val done = CountDownLatch(1)
                thread { runCatching { runBlocking { Bulk.handlers.getValue("mirror")(phone) } }; done.countDown() }
                run(mac, done)
            }
        } finally {
            LinkFeatures.all -= MacMirror
            Bulk.handlers -= "mirror"
        }
    }

    @Test
    fun `relay whitelists args, forwards input and video, and switches the bridge off on close`() {
        val fake = Fake()
        relay(fake) { mac, done ->
            mac.sendJson(mapOf("t" to "mirror", "on" to true, "maxH" to 1280, "evil" to 1, "id" to 5))
            assertTrue(fake.first.await(5, TimeUnit.SECONDS))
            assertEquals("mirror", fake.cmds[0].first)
            assertEquals(setOf("on", "maxH"), fake.cmds[0].second.keys)

            val key = Json.write(mapOf("k" to "key", "code" to 4)).encodeToByteArray()
            mac.send(BulkFrame.INPUT, key)
            val video = ByteArray(70_000) { 1 }
            fake.on.getValue(BridgeWire.VIDEO)(video)
            val a = mac.read()
            val b = mac.read()
            assertEquals(BulkFrame.VIDEO, a.first)
            assertEquals(video.size - 9, a.second.size - 9 + b.second.size - 9)
            assertEquals(4, a.second[0].toInt() and 4)
            assertEquals(0, b.second[0].toInt() and 4)
            val until = System.nanoTime() + 5_000_000_000
            while (fake.sent.isEmpty() && System.nanoTime() < until) Thread.sleep(10)
            assertEquals(listOf(BridgeWire.INPUT to key.toList()), fake.sent)

            mac.close()
            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertEquals("mirror" to mapOf<String, Any?>("on" to false), fake.cmds.last())
            assertTrue(fake.on.isEmpty())
        }
    }

    @Test
    fun `a slow Mac never blocks the bridge reader and costs one idr per overflow`() {
        val fake = Fake()
        relay(fake) { mac, done ->
            mac.sendJson(mapOf("t" to "mirror", "on" to true))
            assertTrue(fake.first.await(5, TimeUnit.SECONDS))
            val until = System.nanoTime() + 5_000_000_000
            while (fake.on[BridgeWire.VIDEO] == null && System.nanoTime() < until) Thread.sleep(10)
            val p = ByteArray(60_000)
            val t0 = System.nanoTime()
            repeat(3000) { fake.on.getValue(BridgeWire.VIDEO)(p) }
            assertTrue(System.nanoTime() - t0 < 4_000_000_000)
            assertEquals(1, fake.sent.count { it.first == BridgeWire.INPUT })
            mac.close()
            assertTrue(done.await(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `a bridge side stop is forwarded and ends the session`() {
        val fake = Fake()
        relay(fake) { mac, done ->
            mac.sendJson(mapOf("t" to "mirror", "on" to true))
            assertTrue(fake.first.await(5, TimeUnit.SECONDS))
            val until = System.nanoTime() + 5_000_000_000
            while (fake.on[BridgeWire.JSON] == null && System.nanoTime() < until) Thread.sleep(10)
            val gone = Json.write(mapOf("t" to "mirror", "ok" to false, "why" to "encoder")).encodeToByteArray()
            fake.on.getValue(BridgeWire.JSON)(gone)
            val m = mac.read()
            assertEquals(BulkFrame.JSON, m.first)
            assertEquals("encoder", Json.obj(m.second.decodeToString())["why"])
            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertTrue(fake.on.isEmpty())
        }
    }
}
