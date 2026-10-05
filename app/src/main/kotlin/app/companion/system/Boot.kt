package app.companion.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.companion.ai.Dl
import app.companion.ai.ModelJobs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Boot : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Alarms.all(c)
                Meetings.sync(c)
                if (Dl.wants(c)) ModelJobs.fallback(c)
            } finally {
                done.finish()
            }
        }
    }
}
