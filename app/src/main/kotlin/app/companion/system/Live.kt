package app.companion.system

import android.app.Application
import android.content.Context

object Live {
    fun boot(app: Application) {}

    suspend fun refresh(c: Context) {}
}
