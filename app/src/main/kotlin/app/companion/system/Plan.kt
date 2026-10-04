package app.companion.system

import android.content.Context
import app.companion.data.Task
import app.companion.sl
import app.companion.ui.dateOf

object Plan {
    suspend fun save(c: Context, t: Task): Task = c.sl.repo.saveTask(t).also { Alarms.set(c, it) }

    suspend fun remind(c: Context, title: String, at: Long, ref: String? = null): Task =
        save(c, Task(title = title, remindAt = at, due = dateOf(at).toEpochDay(), ref = ref))

    suspend fun finish(c: Context, id: Long) {
        c.sl.repo.finish(id)?.let { Alarms.set(c, it) }
    }

    suspend fun reopen(c: Context, id: Long) {
        c.sl.repo.reopen(id)?.let { Alarms.set(c, it) }
    }

    suspend fun remove(c: Context, id: Long) {
        Alarms.cancel(c, id)
        c.sl.repo.removeTask(id)
    }
}
