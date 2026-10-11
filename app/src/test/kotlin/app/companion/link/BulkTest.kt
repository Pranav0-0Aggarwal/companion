package app.companion.link

import app.companion.core.BulkCrypto
import app.companion.core.BulkFrame
import app.companion.core.BulkPipe
import app.companion.link.bulk.Bulk
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

class BulkTest {
    @Test
    fun `closeAll ends a handler blocked on read`() {
        val okm = ByteArray(64) { 7 }
        val token = ByteArray(16)
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { srv ->
            val sock = Socket(InetAddress.getLoopbackAddress(), srv.localPort)
            srv.accept().use {
                val done = CountDownLatch(1)
                val pipe = BulkPipe(sock, BulkFrame(BulkCrypto.phone(okm, token, "call")), "call", null)
                Bulk.start(sock, pipe) { p -> try { p.read() } finally { done.countDown() } }
                Bulk.closeAll()
                assertTrue(done.await(2, TimeUnit.SECONDS))
            }
        }
    }
}
