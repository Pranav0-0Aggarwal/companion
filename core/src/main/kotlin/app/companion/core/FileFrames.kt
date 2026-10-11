package app.companion.core

import java.io.InputStream

object FileFrames {
    const val CHUNK = 65_536

    class Item(val name: String, val size: Long, val mime: String, val input: InputStream) : AutoCloseable {
        override fun close() = input.close()
    }

    interface Sink {
        fun begin(name: String, size: Long)
        fun write(b: ByteArray)
        fun finish()
        fun abort()
    }

    fun send(pipe: BulkPipe, items: List<Item>): Int {
        try {
            val buf = ByteArray(CHUNK)
            for (i in items) {
                pipe.sendJson(mapOf("t" to "file", "name" to i.name, "size" to i.size, "mime" to i.mime))
                var left = i.size
                while (true) {
                    val n = i.input.read(buf)
                    if (n < 0) break
                    left -= n
                    if (left < 0) throw LinkError("size")
                    pipe.send(BulkFrame.DATA, buf.copyOf(n))
                }
                if (left != 0L) throw LinkError("size")
            }
            pipe.sendJson(mapOf("t" to "end"))
            while (true) {
                val (t, b) = pipe.read()
                if (t != BulkFrame.JSON) throw LinkError("type")
                val m = Json.obj(b.decodeToString())
                if (m["t"] == "end") return (m["saved"] as? Number)?.toInt() ?: throw LinkError("saved")
            }
        } finally {
            items.forEach { runCatching(it::close) }
        }
    }

    fun receive(pipe: BulkPipe, sink: Sink): Int {
        var n = 0
        var want = -1L
        var got = 0L
        try {
            while (true) {
                val (t, b) = pipe.read()
                if (t == BulkFrame.DATA) {
                    got += b.size
                    if (want < 0 || got > want) throw LinkError("size")
                    sink.write(b)
                    continue
                }
                if (t != BulkFrame.JSON) throw LinkError("type")
                val m = Json.obj(b.decodeToString())
                if (want >= 0) {
                    if (got != want) throw LinkError("size")
                    sink.finish()
                    n++
                    want = -1
                }
                when (m["t"]) {
                    "file" -> {
                        want = (m["size"] as? Number)?.toLong()?.takeIf { it >= 0 } ?: throw LinkError("size")
                        got = 0
                        sink.begin(m["name"] as? String ?: throw LinkError("name"), want)
                    }
                    "end" -> {
                        pipe.sendJson(mapOf("t" to "end", "saved" to n))
                        return n
                    }
                    else -> throw LinkError("type")
                }
            }
        } catch (e: Throwable) {
            if (want >= 0) runCatching(sink::abort)
            throw e
        }
    }
}
