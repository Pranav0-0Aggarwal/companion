package app.companion.core

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

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
    fun get(url: String, from: Long): Reply
}

class Fetcher(private val net: Net, private val first: String = "github.com", private val next: String = "release-assets.githubusercontent.com") {
    enum class Out { Done, Retry, Bad }

    fun pull(url: String, size: Long, sha: String, part: File, dest: File, live: () -> Boolean, tick: (Long) -> Unit): Out {
        if (size <= 0 || sha.length != 64) return Out.Bad
        part.parentFile?.mkdirs()
        if (part.length() > size) part.delete()
        if (part.length() < size) {
            val o = try {
                get(url, size, part, live, tick)
            } catch (_: IOException) {
                Out.Retry
            }
            if (o != Out.Done) return o
        }
        if (part.length() != size || Hash.sha256(part) != sha) {
            part.delete()
            return Out.Bad
        }
        return try {
            dest.parentFile?.mkdirs()
            Files.move(part.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE)
            Out.Done
        } catch (_: IOException) {
            Out.Bad
        }
    }

    private fun ok(u: URI, hop: Int) = u.scheme == "https" && u.userInfo == null && (u.port == -1 || u.port == 443) && u.host == if (hop == 0) first else next

    private fun get(start: String, size: Long, part: File, live: () -> Boolean, tick: (Long) -> Unit): Out {
        var url = start
        val have = part.length()
        for (hop in 0..MAX_HOPS) {
            if (!ok(URI.create(url), hop)) return Out.Bad
            val r = net.get(url, have)
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

    private companion object {
        const val MAX_HOPS = 4
    }
}
