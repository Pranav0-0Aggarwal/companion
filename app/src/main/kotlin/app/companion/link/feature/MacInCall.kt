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

        fun current(drop: Call? = null): Call? {
            val l = live?.calls.orEmpty().filter { it !== drop && it.state != Call.STATE_DISCONNECTED && it.state != Call.STATE_DISCONNECTING }
            return l.firstOrNull { it.state == Call.STATE_RINGING } ?: l.firstOrNull()
        }
    }
}
