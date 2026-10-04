package app.companion.system

import android.content.Context
import app.companion.data.Repo
import app.companion.data.Task
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import org.json.JSONObject

class GTasks(private val c: Context, private val repo: Repo) {
    private val base = "https://tasks.googleapis.com/tasks/v1/lists/@default/tasks"

    private fun call(token: String, url: String, method: String = "GET", body: JSONObject? = null): JSONObject? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 15_000
            c.readTimeout = 15_000
            c.setRequestProperty("Authorization", "Bearer $token")
            if (method == "PATCH") c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            c.requestMethod = if (method == "GET" || method == "DELETE") method else "POST"
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            if (c.responseCode !in 200..299) null else c.inputStream.use { s -> s.readBytes().decodeToString().ifBlank { "{}" }.let(::JSONObject) }
        } finally {
            c.disconnect()
        }
    }

    private fun json(t: Task) = JSONObject().apply {
        put("title", t.title)
        put("notes", t.note.orEmpty())
        put("status", if (t.done) "completed" else "needsAction")
        t.due?.let { put("due", "${LocalDate.ofEpochDay(it)}T00:00:00.000Z") }
    }

    private fun millis(s: String) = Instant.parse(s).toEpochMilli()

    private fun remote(token: String, since: Long): List<JSONObject>? {
        val out = mutableListOf<JSONObject>()
        var page: String? = null
        do {
            val q = buildString {
                append("$base?showCompleted=true&showHidden=true&showDeleted=true&maxResults=100")
                if (since > 0) append("&updatedMin=${Instant.ofEpochMilli(since)}")
                if (page != null) append("&pageToken=$page")
            }
            val r = call(token, q) ?: return null
            val items = r.optJSONArray("items")
            for (i in 0 until (items?.length() ?: 0)) out.add(items!!.getJSONObject(i))
            page = r.optString("nextPageToken").ifEmpty { null }
        } while (page != null)
        return out
    }

    suspend fun sync(token: String): Boolean {
        val since = repo.profileNow().since?.toLongOrNull() ?: 0L
        val theirs = remote(token, since) ?: return false
        val mine = repo.allTasks()
        for (r in theirs) {
            val id = r.getString("id")
            val l = mine.firstOrNull { it.gid == id }
            val at = millis(r.optString("updated", Instant.now().toString()))
            when {
                r.optBoolean("deleted") -> l?.let { repo.purgeTask(it.id) }
                l == null -> repo.saveTask(
                    Task(
                        title = r.optString("title"), note = r.optString("notes").ifEmpty { null },
                        due = r.optString("due").takeIf { it.length >= 10 }?.let { LocalDate.parse(it.take(10)).toEpochDay() },
                        done = r.optString("status") == "completed", gid = id,
                    ),
                ).also { Alarms.set(c, it) }
                at > l.upd && !l.gone -> repo.saveTask(
                    l.copy(
                        title = r.optString("title"), note = r.optString("notes").ifEmpty { null },
                        due = r.optString("due").takeIf { it.length >= 10 }?.let { LocalDate.parse(it.take(10)).toEpochDay() },
                        done = r.optString("status") == "completed",
                    ),
                ).also { Alarms.set(c, it) }
            }
        }
        for (l in repo.allTasks()) {
            when {
                l.gone -> {
                    l.gid?.let { call(token, "$base/$it", "DELETE") }
                    repo.purgeTask(l.id)
                }
                l.gid == null -> call(token, base, "POST", json(l))?.optString("id")?.takeIf { it.isNotEmpty() }?.let { repo.saveTask(l.copy(gid = it)) }
                l.upd > since -> call(token, "$base/${l.gid}", "PATCH", json(l))
            }
        }
        repo.edit { it.copy(since = System.currentTimeMillis().toString()) }
        return true
    }
}
