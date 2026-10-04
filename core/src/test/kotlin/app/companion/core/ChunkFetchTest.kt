package app.companion.core

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChunkFetchTest {
    private val dir: File = Files.createTempDirectory("chunk").toFile()
    private val data = ByteArray(3_000_000) { (it * 131 + it / 7).toByte() }
    private val size = data.size.toLong()
    private val sha = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
    private val part = File(dir, "x.part")
    private val side = File(dir, "x.part.ranges")
    private val dest = File(dir, "out/x.bin")
    private val start = "https://a.test/models/x.bin"

    private lateinit var srv: HttpServer
    private val starts = AtomicInteger()
    private val blobs = AtomicInteger()
    private val live = AtomicInteger()
    private val peak = AtomicInteger()
    private val served = AtomicLong()
    private val asked = ConcurrentLinkedQueue<Pair<Long, Long>>()
    private val hosts = ConcurrentLinkedQueue<String>()

    @Volatile private var sig = 1

    @Volatile private var ranges = true

    @Volatile private var flip = false

    @Volatile private var expireAt = Int.MAX_VALUE

    @BeforeTest
    fun up() {
        srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        srv.executor = Executors.newFixedThreadPool(8)
        srv.createContext("/") { x -> serve(x) }
        srv.start()
    }

    @AfterTest
    fun down() {
        srv.stop(0)
        dir.deleteRecursively()
    }

    private fun serve(x: HttpExchange) {
        x.use {
            val path = x.requestURI.path
            if (path == "/models/x.bin") {
                starts.incrementAndGet()
                x.responseHeaders.add("Location", "https://b.test/blob/x?sig=$sig")
                x.sendResponseHeaders(302, -1)
                return
            }
            val n = blobs.incrementAndGet()
            if (n == expireAt) sig++
            if (x.requestURI.query != "sig=$sig") {
                x.sendResponseHeaders(403, -1)
                return
            }
            val body = if (flip) data.copyOf().also { it[1_234_567] = (it[1_234_567] + 1).toByte() } else data
            val h = x.requestHeaders.getFirst("Range")
            if (h == null || !ranges) {
                x.sendResponseHeaders(200, size)
                x.responseBody.write(body)
                return
            }
            val (a, b) = h.removePrefix("bytes=").split("-").let { it[0].toLong() to it[1].toLong() }
            if (b > 0) asked.add(a to b)
            val now = live.incrementAndGet()
            peak.accumulateAndGet(now) { p, q -> maxOf(p, q) }
            try {
                Thread.sleep(20)
                x.responseHeaders.add("Content-Range", "bytes $a-$b/$size")
                x.sendResponseHeaders(206, b - a + 1)
                x.responseBody.write(body, a.toInt(), (b - a + 1).toInt())
                served.addAndGet(if (b == 0L) 0 else b - a + 1)
            } finally {
                live.decrementAndGet()
            }
        }
    }

    private val net = Net { u, from, to ->
        val uri = URI.create(u)
        hosts.add(uri.host)
        val c = URL("http://127.0.0.1:${srv.address.port}${uri.rawPath}${uri.rawQuery?.let { "?$it" } ?: ""}").openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        if (from > 0 || to >= 0) c.setRequestProperty("Range", "bytes=$from-${if (to >= 0) to else ""}")
        val code = c.responseCode
        Reply(code, c.getHeaderField("Location"), c.getHeaderField("Content-Range"), if (code == 200 || code == 206) c.inputStream else InputStream.nullInputStream())
    }

    private fun fetcher(n: Net = net, free: (File) -> Long = { Long.MAX_VALUE }) = Fetcher(n, "a.test", "b.test", 4, 1_000_000, 200_000, free)

    private fun pull(f: Fetcher = fetcher(), live: () -> Boolean = { true }) = f.pull(start, size, sha, part, dest, live) {}

    @Test
    fun `four connections fetch ranges in parallel and the whole file is verified`() {
        assertEquals(Fetcher.Out.Done, pull())
        assertContentEquals(data, dest.readBytes())
        assertFalse(part.exists())
        assertFalse(side.exists())
        assertEquals(1, starts.get())
        assertEquals(size, served.get())
        assertTrue(peak.get() in 2..4, "peak ${peak.get()}")
        assertTrue(asked.all { (a, b) -> a >= 0 && b < size && b - a < 200_000 })
        assertEquals("a.test", hosts.first())
        assertTrue(hosts.drop(1).all { it == "b.test" })
    }

    @Test
    fun `a signed link that expires is resolved again on 403`() {
        expireAt = 6
        assertEquals(Fetcher.Out.Done, pull())
        assertContentEquals(data, dest.readBytes())
        assertTrue(starts.get() in 2..9, "starts ${starts.get()}")
    }

    @Test
    fun `resume with a sidecar requests only the missing ranges`() {
        val have = listOf(Seg(0, 800_000), Seg(1_500_000, 2_000_000))
        val junk = ByteArray(data.size) { 0x55 }
        have.forEach { System.arraycopy(data, it.from.toInt(), junk, it.from.toInt(), it.len.toInt()) }
        part.writeBytes(junk)
        val b = Book(side, size, sha)
        have.forEach(b::add)
        assertEquals(Fetcher.Out.Done, pull())
        assertContentEquals(data, dest.readBytes())
        assertEquals(size - 1_300_000, served.get())
        assertTrue(asked.none { (a, e) -> have.any { a <= it.to - 1 && e >= it.from } })
        assertFalse(side.exists())
    }

    @Test
    fun `a sidecar for another checksum is ignored and everything is fetched`() {
        part.writeBytes(ByteArray(data.size) { 1 })
        Book(side, size, "b".repeat(64)).add(Seg(0, 2_000_000))
        assertEquals(Fetcher.Out.Done, pull())
        assertContentEquals(data, dest.readBytes())
        assertEquals(size, served.get())
    }

    @Test
    fun `a plain partial file from a single stream resumes after its prefix`() {
        part.writeBytes(data.copyOf(900_000))
        assertEquals(Fetcher.Out.Done, pull())
        assertContentEquals(data, dest.readBytes())
        assertEquals(size - 900_000, served.get())
        assertTrue(asked.all { it.first >= 900_000 })
    }

    @Test
    fun `pausing keeps the completed ranges and the next run finishes the rest`() {
        assertEquals(Fetcher.Out.Retry, pull(live = { served.get() < 1_000_000 }))
        val first = served.get()
        val book = Book(side, size, sha)
        assertTrue(book.known() && book.done() > 0 && book.done() < size)
        assertTrue(part.exists())
        assertFalse(dest.exists())
        assertEquals(Fetcher.Out.Done, pull())
        assertContentEquals(data, dest.readBytes())
        assertEquals(size - book.done(), served.get() - first)
    }

    @Test
    fun `corrupt data fails the checksum and wipes the part and the sidecar`() {
        flip = true
        assertEquals(Fetcher.Out.Bad, pull())
        assertFalse(part.exists())
        assertFalse(side.exists())
        assertFalse(dest.exists())
    }

    @Test
    fun `a server that ignores range falls back to one stream`() {
        ranges = false
        assertEquals(Fetcher.Out.Done, pull())
        assertContentEquals(data, dest.readBytes())
    }

    @Test
    fun `not enough free space is not retried and nothing is requested`() {
        assertEquals(Fetcher.Out.Full, pull(fetcher(free = { 1_000 })))
        assertTrue(hosts.isEmpty())
    }

    @Test
    fun `a write failure with no space left on a chunk is not retried`() {
        val n = Net { _, from, to ->
            if (to == 0L) Reply(206, null, "bytes 0-0/$size", ByteArrayInputStream(ByteArray(1)))
            else throw IOException("write failed: ENOSPC (No space left on device)")
        }
        assertEquals(Fetcher.Out.Full, pull(fetcher(n)))
    }

    @Test
    fun `a dropped connection mid chunk is retried`() {
        val n = Net { _, _, to ->
            if (to == 0L) Reply(206, null, "bytes 0-0/$size", ByteArrayInputStream(ByteArray(1))) else throw IOException("connection reset")
        }
        assertEquals(Fetcher.Out.Retry, pull(fetcher(n)))
        assertFalse(dest.exists())
    }

    @Test
    fun `redirect rules and the size check apply to the range probe`() {
        val evil = Net { u, _, _ -> if (u == start) Reply(302, "https://evil.test/x", null, null) else Reply(206, null, "bytes 0-0/$size", ByteArrayInputStream(ByteArray(1))) }
        assertEquals(Fetcher.Out.Bad, pull(fetcher(evil)))
        val plain = Net { u, _, _ -> if (u == start) Reply(302, "http://b.test/x", null, null) else Reply(206, null, "bytes 0-0/$size", ByteArrayInputStream(ByteArray(1))) }
        assertEquals(Fetcher.Out.Bad, pull(fetcher(plain)))
        val wrong = Net { u, _, _ -> if (u == start) Reply(302, "https://b.test/x", null, null) else Reply(206, null, "bytes 0-0/${size + 1}", ByteArrayInputStream(ByteArray(1))) }
        assertEquals(Fetcher.Out.Bad, pull(fetcher(wrong)))
        assertFalse(part.exists() && part.length() > 0)
    }

    @Test
    fun `unpinned entries fail closed before any request`() {
        assertEquals(Fetcher.Out.Bad, fetcher().pull(start, 0, sha, part, dest, { true }) {})
        assertEquals(Fetcher.Out.Bad, fetcher().pull(start, size, "", part, dest, { true }) {})
        assertTrue(hosts.isEmpty())
    }
}
