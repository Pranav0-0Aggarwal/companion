package app.companion.link.feature

import android.telecom.Call
import android.telecom.InCallService

class MacInCall : InCallService() {
    private val cb = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            Calls.live?.report()
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            Calls.live?.report()
        }
    }

    override fun onCreate() {
        super.onCreate()
        live = this
    }

    override fun onCallAdded(call: Call) {
        call.registerCallback(cb)
        Calls.live?.report()
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(cb)
        Calls.live?.report(call)
    }

    override fun onDestroy() {
        if (live === this) live = null
        Calls.live?.report()
        super.onDestroy()
    }

    companion object {
        @Volatile var live: MacInCall? = null

        fun current(drop: Call? = null): Call? = pick(live?.calls.orEmpty().filter { it !== drop && it.parent == null }) { it.state }

        fun <T> pick(l: List<T>, state: (T) -> Int): T? = l.filter { rank(state(it)) >= 0 }.minByOrNull { rank(state(it)) }

        fun rank(s: Int) = when (s) {
            Call.STATE_RINGING, Call.STATE_SIMULATED_RINGING -> 0
            Call.STATE_ACTIVE, Call.STATE_AUDIO_PROCESSING -> 1
            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> -1
            Call.STATE_HOLDING -> 3
            else -> 2
        }

        fun name(s: Int) = when (rank(s)) {
            0 -> "ringing"
            1 -> "active"
            3 -> "held"
            else -> "dialing"
        }
    }
}
