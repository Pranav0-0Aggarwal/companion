package app.companion.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.companion.core.Repeat
import app.companion.sl
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Remind : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getLongExtra(ID, 0)
        val action = i.action ?: return
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                run(c, id, action)
            } finally {
                done.finish()
            }
        }
    }

    private suspend fun run(c: Context, id: Long, action: String) {
        val repo = c.sl.repo
        val t = repo.task(id) ?: return
        val now = System.currentTimeMillis()
        when (action) {
            FIRE -> {
                Notices.fire(c, t, repo.profileNow().name)
                val next = t.remindAt?.let { Repeat.after(it, t.repeat, now, ZoneId.systemDefault()) }
                if (next != null) Alarms.set(c, repo.saveTask(t.copy(remindAt = next)))
            }
            NEAR -> Notices.near(c, t)
            DONE -> {
                Notices.clear(c, id)
                if (t.repeat == null) Plan.finish(c, id)
            }
            SNOOZE -> {
                Notices.clearAlert(c, id)
                Alarms.set(c, repo.saveTask(t.copy(remindAt = now + 3_600_000L)))
            }
        }
    }

    companion object {
        const val ID = "id"
        const val FIRE = "app.companion.FIRE"
        const val NEAR = "app.companion.NEAR"
        const val DONE = "app.companion.DONE"
        const val SNOOZE = "app.companion.SNOOZE"
        val codes = mapOf(FIRE to 0, NEAR to 1, DONE to 2, SNOOZE to 3)
    }
}
