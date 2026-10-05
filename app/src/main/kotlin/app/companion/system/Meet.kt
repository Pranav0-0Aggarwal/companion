package app.companion.system

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import app.companion.core.Meets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Meet : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val act = i.action?.takeIf { it in acts } ?: return
        val at = i.data?.schemeSpecificPart?.split(':')?.mapNotNull(String::toLongOrNull)?.takeIf { it.size == 2 } ?: return
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Meetings.fire(c, act, at[0], at[1])
            } finally {
                done.finish()
            }
        }
    }

    companion object {
        const val HEADS = "app.companion.MEET_HEADS"
        const val LIVE = "app.companion.MEET_LIVE"
        const val TICK = "app.companion.MEET_TICK"
        private val acts = setOf(HEADS, LIVE, TICK)
    }
}

class Joined : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val k = intent.getStringExtra(KEY)
        val url = Meets.join(intent.getStringExtra(URL))?.url
        if (k != null && url != null) Meetings.open(this, k, url)
        finish()
    }

    companion object {
        const val KEY = "key"
        const val URL = "url"
    }
}
