package app.companion.ingest

import app.companion.BuildConfig
import app.companion.core.Raw
import app.companion.core.Source
import app.companion.data.Repo
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class Gmail(private val repo: Repo, private val ingest: Ingest) {
    val enabled = BuildConfig.GMAIL_CLIENT_ID.isNotBlank()

    suspend fun sync(token: String): Int = withContext(Dispatchers.IO) {
        val p = repo.profileNow()
        if (!p.mail) return@withContext 0
        val mark = get(token, "profile")?.getString("historyId") ?: return@withContext 0
        val added = p.hist?.let { added(token, it) }
        var n = 0
        for (id in added ?: listed(token)) {
            val m = get(token, "messages/$id?format=full") ?: continue
            val raw = raw(m)
            if (added != null && !fits(m, raw)) continue
            ingest.handle(raw, "mail|$id")
            n++
        }
        repo.edit { it.copy(hist = mark) }
        n
    }

    private fun listed(token: String): List<String> {
        val q = URLEncoder.encode(QUERY, "UTF-8")
        return get(token, "messages?maxResults=50&q=$q")?.optJSONArray("messages").items().map { it.getString("id") }
    }

    private fun added(token: String, start: String): List<String>? {
        val ids = LinkedHashSet<String>()
        var page: String? = null
        do {
            val next = page?.let { "&pageToken=$it" }.orEmpty()
            val r = get(token, "history?startHistoryId=$start&historyTypes=messageAdded$next") ?: return null
            r.optJSONArray("history").items().forEach { h ->
                h.optJSONArray("messagesAdded").items().forEach { ids.add(it.getJSONObject("message").getString("id")) }
            }
            page = r.optString("nextPageToken").ifEmpty { null }
        } while (page != null)
        return ids.toList()
    }

    private fun raw(m: JSONObject): Raw {
        val payload = m.getJSONObject("payload")
        val headers = payload.optJSONArray("headers").items()
        fun header(name: String) = headers.firstOrNull { it.getString("name").equals(name, true) }?.getString("value").orEmpty()
        val body = plain(payload) ?: m.optString("snippet")
        return Raw(Source.Mail, header("From"), header("Subject"), body.take(BODY), m.getString("internalDate").toLong())
    }

    private fun plain(part: JSONObject): String? {
        val data = part.optJSONObject("body")?.optString("data").orEmpty()
        if (part.optString("mimeType") == "text/plain" && data.isNotEmpty()) return String(Base64.getUrlDecoder().decode(data))
        return part.optJSONArray("parts").items().firstNotNullOfOrNull { plain(it) }
    }

    private fun fits(m: JSONObject, r: Raw): Boolean {
        val labels = m.optJSONArray("labelIds")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()
        val inbox = "CATEGORY_PERSONAL" in labels || "CATEGORY_UPDATES" in labels
        return inbox && WORDS.containsMatchIn(r.title + " " + m.optString("snippet"))
    }

    private fun get(token: String, path: String): JSONObject? {
        val c = URL(API + path).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = TIMEOUT
            c.readTimeout = TIMEOUT
            c.setRequestProperty("Authorization", "Bearer $token")
            return when (val code = c.responseCode) {
                200 -> JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                404 -> null
                else -> throw IOException("gmail $code")
            }
        } finally {
            c.disconnect()
        }
    }

    private fun JSONArray?.items(): List<JSONObject> = if (this == null) emptyList() else List(length()) { getJSONObject(it) }

    private companion object {
        const val API = "https://gmail.googleapis.com/gmail/v1/users/me/"
        const val QUERY = "category:primary OR category:updates (statement OR receipt OR invoice OR bill OR order OR booking OR delivered)"
        const val TIMEOUT = 15_000
        const val BODY = 4000
        val WORDS = Regex("statement|receipt|invoice|bill|order|booking|delivered", RegexOption.IGNORE_CASE)
    }
}
