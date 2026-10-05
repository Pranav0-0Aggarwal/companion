package app.companion.data

import android.content.Context
import android.util.Base64
import app.companion.core.DocKind
import app.companion.core.Expiry
import app.companion.core.Json
import app.companion.core.Mask
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class Revealed(val number: String?, val extra: Map<String, String>)

class Docs(private val c: Context, private val db: Db) {
    private val d = db.life()
    private val zone get() = ZoneId.systemDefault()

    fun list() = d.docs()

    suspend fun all() = d.docsNow()

    suspend fun find(q: String): List<DocRow> {
        val words = q.lowercase().split(Regex("\\s+")).filter { it.length > 1 }
        return all().filter { r -> val hay = "${r.kind} ${r.title} ${r.mask.orEmpty()}".lowercase(); words.isEmpty() || words.any { it in hay } }
    }

    suspend fun has(kind: DocKind, number: String?): Boolean {
        val m = number?.takeIf { it.isNotBlank() }?.let(Mask::number) ?: return false
        return all().any { it.kind == kind.name && it.mask == m }
    }

    suspend fun add(kind: DocKind, title: String, number: String?, expires: LocalDate?, extra: Map<String, String>, src: String, photo: ByteArray? = null, now: Long = System.currentTimeMillis()): Long {
        val key = photo?.let { VaultCrypto.newKey() }
        val file = photo?.let {
            File(VaultCrypto.dir(c), "${UUID.randomUUID()}.bin").also { f -> f.writeBytes(VaultCrypto.lock(key!!, it)) }.name
        }
        val body = buildMap<String, Any?> {
            put("number", number)
            put("extra", extra)
            if (key != null) put("key", Base64.encodeToString(key, Base64.NO_WRAP))
        }
        val row = DocRow(
            kind = kind.name, title = title, fields = VaultCrypto.seal(Json.write(body).toByteArray()),
            expires = expires?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(), photo = file, src = src, at = now,
            mask = number?.takeIf { it.isNotBlank() }?.let(Mask::number), sent = "",
        )
        return d.addDoc(row).also { _ ->
            row.expires?.let { app.companion.system.VaultPrefs.mirror(c, db.dao().profileNow()?.cal, kind, title, it) }
        }
    }

    suspend fun reveal(id: Long): Revealed? {
        if (!VaultCrypto.authed()) throw Locked()
        val r = d.doc(id) ?: return null
        val plain = VaultCrypto.open(r.fields)
        if (VaultCrypto.weak(r.fields)) d.updateDoc(r.copy(fields = VaultCrypto.seal(plain)))
        val m = Json.obj(plain.decodeToString())
        @Suppress("UNCHECKED_CAST")
        return Revealed(m["number"] as? String, (m["extra"] as? Map<String, Any?>).orEmpty().mapValues { it.value.toString() })
    }

    suspend fun photo(id: Long): ByteArray? {
        if (!VaultCrypto.authed()) throw Locked()
        val r = d.doc(id) ?: return null
        val f = r.photo?.let { File(VaultCrypto.dir(c), it) }?.takeIf { it.isFile } ?: return null
        val key = (Json.obj(VaultCrypto.open(r.fields).decodeToString())["key"] as? String)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        return VaultCrypto.unlock(key, f.readBytes())
    }

    suspend fun remove(id: Long) {
        d.doc(id)?.photo?.let { File(VaultCrypto.dir(c), it).delete() }
        d.dropDoc(id)
    }

    suspend fun expiring() = d.expiring()

    suspend fun sent(r: DocRow, mark: Int) {
        d.updateDoc(r.copy(sent = Expiry.mask(Expiry.parseSent(r.sent.orEmpty()) + mark)))
    }
}
