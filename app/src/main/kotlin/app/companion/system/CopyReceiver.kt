package app.companion.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.companion.sl
import app.companion.ui.copy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CopyReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        i.getStringExtra(Notes.CODE)?.let { copy(c, it) }
        val id = i.getLongExtra(Notes.DROP, 0).takeIf { it != 0L } ?: return
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                c.sl.repo.dropOtp(id)
                Live.refresh(c)
            } finally {
                done.finish()
            }
        }
    }
}
