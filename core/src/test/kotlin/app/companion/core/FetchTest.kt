package app.companion.core

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FetchTest {
    private val dir: File = Files.createTempDirectory("fetch").toFile()
    private val data = ByteArray(300_000) { (it * 31 + 7).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
    private val part = File(dir, "x.part")
    private val dest = File(dir, "out/x.bin")
    private val start = "https://a.test/models/x.bin"
    private val cdn = "https://b.test/blob/x"

    @AfterTest
    fun clean() {
        dir.deleteRecursively()
    }

    private class Served(val seen: MutableList<Pair<String, Long>> = mutableListOf())

    private fun net(log: Served = Served(), route: (String, Long, ByteArray) -> Reply) = Net { u, from ->
        log.seen.add(u to from)
        route(u, from, data)
    }

    private fun ok(from: Long, d: ByteArray) = Reply(206, null, "bytes $from-${d.size - 1}/${d.size}", ByteArrayInputStream(d, from.toInt(), d.size - from.toInt()))

    private val hop = Reply(302, cdn, null, null)

    private fun fetcher(n: Net) = Fetcher(n, "a.test", "b.test")

    private fun pull(f: Fetcher, size: Long = data.size.toLong(), hash: String = sha, live: () -> Boolean = { true }) =
        f.pull(start, size, hash, part, dest, live) {}

    @Test
    fun `downloads through one allowed redirect and installs atomically`() {
        val log = Served()
        val out = pull(fetcher(net(log) { u, from, d -> if (u == start) hop else ok(from, d) }))
        assertEquals(Fetcher.Out.Done, out)
        assertContentEquals(data, dest.readBytes())
        assertFalse(part.exists())
        assertEquals(listOf(start to 0L, cdn to 0L), log.seen)
    }

    @Test
    fun `resumes a partial file with a range request`() {
        part.writeBytes(data.copyOf(100_000))
        val log = Served()
        val out = pull(fetcher(net(log) { _, from, d -> ok(from, d) }))
        assertEquals(Fetcher.Out.Done, out)
        assertContentEquals(data, dest.readBytes())
        assertEquals(100_000L, log.seen.first().second)
    }

    @Test
    fun `a server that ignores range restarts from zero`() {
        part.writeBytes(ByteArray(50_000) { 9 })
        val out = pull(fetcher(net { _, _, d -> Reply(200, null, null, ByteArrayInputStream(d)) }))
        assertEquals(Fetcher.Out.Done, out)
        assertContentEquals(data, dest.readBytes())
    }

    @Test
    fun `a complete part file is only verified`() {
        part.writeBytes(data)
        var calls = 0
        val out = pull(fetcher(net { _, _, _ -> calls++; hop }))
        assertEquals(Fetcher.Out.Done, out)
        assertEquals(0, calls)
    }

    @Test
    fun `redirect to another host is refused`() {
        val out = pull(fetcher(net { u, from, d -> if (u == start) Reply(302, "https://evil.test/x", null, null) else ok(from, d) }))
        assertEquals(Fetcher.Out.Bad, out)
        assertFalse(dest.exists())
    }

    @Test
    fun `redirect to plain http is refused`() {
        val out = pull(fetcher(net { u, from, d -> if (u == start) Reply(302, "http://b.test/blob/x", null, null) else ok(from, d) }))
        assertEquals(Fetcher.Out.Bad, out)
    }

    @Test
    fun `second hop must not stay on the first host and must not loop`() {
        assertEquals(Fetcher.Out.Bad, pull(fetcher(net { u, _, _ -> if (u == start) Reply(302, start, null, null) else hop })))
        assertEquals(Fetcher.Out.Bad, pull(fetcher(net { _, _, _ -> Reply(302, cdn, null, null) })))
    }

    @Test
    fun `non https start and hosts with credentials or ports are refused`() {
        val f = fetcher(net { _, from, d -> ok(from, d) })
        assertEquals(Fetcher.Out.Bad, f.pull("http://a.test/x", data.size.toLong(), sha, part, dest, { true }) {})
        assertEquals(Fetcher.Out.Bad, f.pull("https://u@a.test/x", data.size.toLong(), sha, part, dest, { true }) {})
        assertEquals(Fetcher.Out.Bad, f.pull("https://a.test:8443/x", data.size.toLong(), sha, part, dest, { true }) {})
    }

    @Test
    fun `wrong checksum deletes the part and installs nothing`() {
        val out = pull(fetcher(net { _, from, d -> ok(from, d) }), hash = "0".repeat(64))
        assertEquals(Fetcher.Out.Bad, out)
        assertFalse(part.exists())
        assertFalse(dest.exists())
    }

    @Test
    fun `unpinned size or checksum fails closed without any request`() {
        var calls = 0
        val f = fetcher(net { _, _, _ -> calls++; hop })
        assertEquals(Fetcher.Out.Bad, pull(f, size = 0))
        assertEquals(Fetcher.Out.Bad, pull(f, hash = ""))
        assertEquals(0, calls)
    }

    @Test
    fun `a body longer than the pinned size is rejected`() {
        val out = pull(fetcher(net { _, _, d -> Reply(200, null, null, ByteArrayInputStream(d + ByteArray(10))) }))
        assertEquals(Fetcher.Out.Bad, out)
        assertFalse(part.exists())
    }

    @Test
    fun `a misaligned content range is refused`() {
        part.writeBytes(data.copyOf(1000))
        val out = pull(fetcher(net { _, _, d -> Reply(206, null, "bytes 0-${d.size - 1}/${d.size}", ByteArrayInputStream(d)) }))
        assertEquals(Fetcher.Out.Bad, out)
    }

    @Test
    fun `cancel keeps the part for resume`() {
        var n = 0
        val out = pull(fetcher(net { _, from, d -> ok(from, d) }), live = { n++ < 2 })
        assertEquals(Fetcher.Out.Retry, out)
        assertTrue(part.length() in 1 until data.size)
        assertFalse(dest.exists())
    }

    @Test
    fun `server errors retry and client errors fail`() {
        assertEquals(Fetcher.Out.Retry, pull(fetcher(net { _, _, _ -> Reply(503, null, null, null) })))
        assertEquals(Fetcher.Out.Bad, pull(fetcher(net { _, _, _ -> Reply(404, null, null, null) })))
    }
}
