package app.companion.core

import java.io.DataInputStream
import java.net.Socket

object BulkAddr {
    fun check(ip: String) {
        val p = ip.split('.').map { s -> s.takeIf { it.length in 1..3 && it.all(Char::isDigit) && (it.length == 1 || it[0] != '0') }?.toInt() }
        require(p.size == 4 && p.all { it != null && it <= 255 } && (p[0] == 10 || p[0] == 172 && p[1]!! in 16..31 || p[0] == 192 && p[1] == 168)) { "ip" }
    }
}

class BulkPipe(private val sock: Socket, private val frame: BulkFrame, val purpose: String, val ref: String?) : AutoCloseable {
    private val inp = DataInputStream(sock.getInputStream())
    private val out = sock.getOutputStream()

    fun send(type: Int, body: ByteArray) = synchronized(out) {
        out.write(frame.seal(type, body))
        out.flush()
    }

    fun sendJson(o: Map<String, Any?>) = send(BulkFrame.JSON, Json.write(o).encodeToByteArray())

    fun read() = frame.read(inp)

    override fun close() = sock.close()
}
