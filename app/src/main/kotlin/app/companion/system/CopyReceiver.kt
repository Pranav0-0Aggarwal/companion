package app.companion.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.companion.ui.copy

class CopyReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        i.getStringExtra(Notes.CODE)?.let { copy(c, it) }
    }
}
