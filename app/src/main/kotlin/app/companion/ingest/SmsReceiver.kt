package app.companion.ingest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import app.companion.core.Raw
import app.companion.core.Source
import app.companion.sl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(i)
        if (parts.isEmpty()) return
        val pending = goAsync()
        val ingest = c.sl.ingest
        CoroutineScope(Dispatchers.IO).launch {
            try {
                parts.groupBy { it.originatingAddress.orEmpty() }.forEach { (from, ms) ->
                    val body = ms.joinToString("") { it.messageBody.orEmpty() }
                    ingest.handle(Raw(Source.Sms, from, "", body, ms.first().timestampMillis))
                }
            } finally {
                pending.finish()
            }
        }
    }
}
