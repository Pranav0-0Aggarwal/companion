package app.companion.core

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

object Hash {
    fun sha256(f: File): String = f.inputStream().use { i ->
        val md = MessageDigest.getInstance("SHA-256")
        val b = ByteArray(1 shl 20)
        while (true) {
            val n = i.read(b)
            if (n < 0) break
            md.update(b, 0, n)
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }
}

class Reply(val code: Int, val location: String?, val range: String?, val body: InputStream?)

fun interface Net {
    fun get(url: String, from: Long, to: Long): Reply
}

class Fetcher(
    private val net: Net,
    private val first: String = "github.com",
    private val next: String = "release-assets.githubusercontent.com",
    private val conns: Int = 4,
    private val big: Long = 32L shl 20,
    private val chunk: Long = 8L shl 20,
    private val free: (File) -> Long = { it.usableSpace },
) {
    enum class Out { Done, Retry, Bad, Full }

    private class Stop(val out: Out) : Exception(null, null, false, false)

    fun pull(url: String, size: Long, sha: String, part: File, dest: File, live: () -> Boolean, tick: (Long) -> Unit): Out {
        if (size <= 0 || sha.length != 64) return Out.Bad
        part.absoluteFile.parentFile.mkdirs()
        val side = File(part.path + ".ranges")
        val o = try {
            if (size > big) ranged(url, size, sha, part, side, live, tick) else single(url, size, part, live, tick)
        } catch (e: Stop) {
            e.out
        } catch (e: IOException) {
            if (full(e)) Out.Full else Out.Retry
        } catch (_: IllegalArgumentException) {
            Out.Bad
        }
        if (o != Out.Done) return o
        if (part.length() != size || Hash.sha256(part) != sha) {
            part.delete()
            Book(side, size, sha).reset()
            return Out.Bad
        }
        return try {
            dest.absoluteFile.parentFile.mkdirs()
            Files.move(part.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE)
            side.delete()
            Out.Done
        } catch (_: IOException) {
            Out.Bad
        }
    }

    private fun full(e: IOException) = e.message?.let { "ENOSPC" in it || "No space left" in it } == true

    private fun ok(u: URI, hop: Int) = u.scheme == "https" && u.userInfo == null && (u.port == -1 || u.port == 443) && u.host == if (hop == 0) first else next

    private fun single(url: String, size: Long, part: File, live: () -> Boolean, tick: (Long) -> Unit): Out {
        if (part.length() > size) part.delete()
        val have = part.length()
        if (have >= size) return Out.Done
        if (free(part.absoluteFile.parentFile) < size - have) return Out.Full
        return get(url, size, part, live, tick)
    }

    private fun get(start: String, size: Long, part: File, live: () -> Boolean, tick: (Long) -> Unit): Out {
        var url = start
        val have = part.length()
        for (hop in 0..MAX_HOPS) {
            if (!ok(URI.create(url), hop)) return Out.Bad
            val r = net.get(url, have, -1)
            when (r.code) {
                301, 302, 303, 307, 308 -> {
                    r.body?.close()
                    url = URI.create(url).resolve(r.location ?: return Out.Bad).toString()
                }
                200, 206 -> {
                    if (r.code == 206 && r.range?.startsWith("bytes $have-") != true) {
                        r.body?.close()
                        return Out.Bad
                    }
                    return copy(r, if (r.code == 206) have else 0, size, part, live, tick)
                }
                416 -> {
                    r.body?.close()
                    part.delete()
                    return Out.Retry
                }
                else -> {
                    r.body?.close()
                    return if (r.code == 429 || r.code in 500..599) Out.Retry else Out.Bad
                }
            }
        }
        return Out.Bad
    }

    private fun copy(r: Reply, from: Long, size: Long, part: File, live: () -> Boolean, tick: (Long) -> Unit): Out {
        val body = r.body ?: return Out.Retry
        var pos = from
        val buf = ByteArray(1 shl 16)
        body.use { i ->
            FileOutputStream(part, from > 0).use { o ->
                while (true) {
                    if (!live()) return Out.Retry
                    val n = i.read(buf)
                    if (n < 0) break
                    if (pos + n > size) {
                        part.delete()
                        return Out.Bad
                    }
                    o.write(buf, 0, n)
                    pos += n
                    tick(pos)
                }
            }
        }
        return if (pos == size) Out.Done else Out.Retry
    }

    private fun resolve(start: String, size: Long): String? {
        var url = start
        for (hop in 0..MAX_HOPS) {
            if (!ok(URI.create(url), hop)) throw Stop(Out.Bad)
            val r = net.get(url, 0, 0)
            r.body?.close()
            when (r.code) {
                301, 302, 303, 307, 308 -> url = URI.create(url).resolve(r.location ?: throw Stop(Out.Bad)).toString()
                206 -> return if (r.range?.startsWith("bytes 0-0/") == true && r.range.endsWith("/$size")) url else throw Stop(Out.Bad)
                200 -> return null
                else -> throw Stop(if (r.code == 429 || r.code in 500..599) Out.Retry else Out.Bad)
            }
        }
        throw Stop(Out.Bad)
    }

    private inner class Link(private val start: String, private val size: Long) {
        private var url = ""
        private var gen = 0
        private var spent = 0

        @Synchronized
        fun open(): Boolean {
            url = resolve(start, size) ?: return false
            return true
        }

        @Synchronized
        fun now() = url to gen

        @Synchronized
        fun renew(seen: Int) {
            if (seen != gen) return
            if (spent++ >= MAX_RENEW) throw Stop(Out.Bad)
            url = resolve(start, size) ?: throw Stop(Out.Bad)
            gen++
        }
    }

    private fun ranged(start: String, size: Long, sha: String, part: File, side: File, live: () -> Boolean, tick: (Long) -> Unit): Out {
        val book = Book(side, size, sha)
        if (book.known() && part.length() != size) book.reset()
        if (!book.known() && part.length() in 1 until size) book.add(Seg(0, part.length()))
        if (book.missing().isEmpty()) return Out.Done
        if (free(part.absoluteFile.parentFile) < book.missing().sumOf { it.len }) return Out.Full
        val link = Link(start, size)
        if (!link.open()) {
            part.delete()
            book.reset()
            return single(start, size, part, live, tick)
        }
        RandomAccessFile(part, "rw").use { it.setLength(size) }
        val got = AtomicLong(book.done())
        tick(got.get())
        val q = ConcurrentLinkedQueue(Ranges.chunks(book.missing(), conns, chunk, MIN))
        val fail = AtomicReference<Out?>()
        val stop = { o: Out -> fail.updateAndGet { if (it != null && it.ordinal >= o.ordinal) it else o }; Unit }
        val alive = { live() && fail.get() == null }
        List(minOf(conns, q.size)) {
            thread(name = "fetch-$it") {
                try {
                    while (alive()) {
                        val s = q.poll() ?: break
                        val o = piece(link, s, part, book, got, alive, tick)
                        if (o != Out.Done) {
                            stop(o)
                            break
                        }
                    }
                } catch (e: Stop) {
                    stop(e.out)
                } catch (e: IOException) {
                    stop(if (full(e)) Out.Full else Out.Retry)
                } catch (_: IllegalArgumentException) {
                    stop(Out.Bad)
                }
            }
        }.forEach { it.join() }
        fail.get()?.let { return it }
        return if (book.missing().isEmpty()) Out.Done else Out.Retry
    }

    private fun piece(link: Link, s: Seg, part: File, book: Book, got: AtomicLong, alive: () -> Boolean, tick: (Long) -> Unit): Out {
        var at = s.from
        var tries = 0
        val buf = ByteArray(1 shl 16)
        RandomAccessFile(part, "rw").use { f ->
            try {
                while (at < s.to) {
                    if (!alive() || tries++ > RETRIES) return Out.Retry
                    val (u, gen) = link.now()
                    val r = net.get(u, at, s.to - 1)
                    when (r.code) {
                        206 -> {
                            val body = r.body
                            if (body == null || r.range?.startsWith("bytes $at-") != true) {
                                body?.close()
                                return Out.Bad
                            }
                            body.use { i ->
                                f.seek(at)
                                while (alive()) {
                                    val n = i.read(buf)
                                    if (n < 0) break
                                    if (at + n > s.to) return Out.Bad
                                    f.write(buf, 0, n)
                                    at += n
                                    tick(got.addAndGet(n.toLong()))
                                }
                            }
                        }
                        403, 410 -> {
                            r.body?.close()
                            link.renew(gen)
                        }
                        else -> {
                            r.body?.close()
                            return if (r.code == 429 || r.code in 500..599) Out.Retry else Out.Bad
                        }
                    }
                }
                return Out.Done
            } finally {
                if (at > s.from && runCatching { f.fd.sync() }.isSuccess) book.add(Seg(s.from, at))
            }
        }
    }

    private companion object {
        const val MAX_HOPS = 4
        const val MAX_RENEW = 8
        const val RETRIES = 4
        const val MIN = 1L shl 20
    }
}
