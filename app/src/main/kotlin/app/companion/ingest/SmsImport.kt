package app.companion.ingest

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.provider.Telephony
import app.companion.ai.Job
import app.companion.ai.Processing
import app.companion.ui.has

class Sms(val id: Long, val from: String, val body: String, val at: Long)

object SmsImport {
    private val uri = Telephony.Sms.Inbox.CONTENT_URI

    fun can(c: Context) = c.has(Manifest.permission.READ_SMS)

    fun enqueue(c: Context) = Processing.start(c, Job.Import, false)

    private fun args(sel: String, vararg a: String) = Bundle().apply {
        putString(ContentResolver.QUERY_ARG_SQL_SELECTION, sel)
        putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, a)
    }

    fun count(c: Context, below: Long): Int {
        if (!c.has(Manifest.permission.READ_SMS)) return 0
        return c.contentResolver.query(uri, arrayOf(Telephony.Sms._ID), args("${Telephony.Sms._ID} < ?", below.toString()), null)?.use { it.count } ?: 0
    }

    fun page(c: Context, below: Long, n: Int): List<Sms> {
        if (n <= 0 || !c.has(Manifest.permission.READ_SMS)) return emptyList()
        val q = args("${Telephony.Sms._ID} < ?", below.toString()).apply {
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${Telephony.Sms._ID} DESC")
            putInt(ContentResolver.QUERY_ARG_LIMIT, n)
        }
        val cols = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        return c.contentResolver.query(uri, cols, q, null)?.use { r ->
            buildList { while (r.moveToNext()) add(Sms(r.getLong(0), r.getString(1).orEmpty(), r.getString(2).orEmpty(), r.getLong(3))) }
        }.orEmpty()
    }

    fun find(c: Context, from: String, at: Long): String? {
        if (!c.has(Manifest.permission.READ_SMS)) return null
        val sel = "(${Telephony.Sms.DATE} = ? OR ${Telephony.Sms.DATE_SENT} = ?) AND ${Telephony.Sms.ADDRESS} = ?"
        return c.contentResolver.query(uri, arrayOf(Telephony.Sms.BODY), args(sel, at.toString(), at.toString(), from), null)?.use { r ->
            if (r.moveToFirst()) r.getString(0) else null
        }
    }
}
