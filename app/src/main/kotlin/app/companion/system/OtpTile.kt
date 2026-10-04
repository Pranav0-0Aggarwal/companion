package app.companion.system

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.companion.data.Item
import app.companion.sl
import app.companion.ui.codeText
import app.companion.ui.copy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OtpTile : TileService() {
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        s.launch { show(newest()) }
    }

    override fun onStopListening() {
        scope?.cancel()
    }

    override fun onClick() {
        unlockAndRun {
            scope?.launch {
                val i = newest()
                i?.code?.let { copy(this@OtpTile, it) }
                show(i)
            }
        }
    }

    private suspend fun newest() = sl.repo.otpsNow().firstOrNull()

    private fun show(i: Item?) {
        val t = qsTile ?: return
        t.label = "Latest OTP"
        t.subtitle = when {
            i == null -> "none live"
            isLocked -> "Unlock to copy"
            else -> i.code?.let(::codeText)
        }
        t.state = if (i == null) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        t.updateTile()
    }
}
