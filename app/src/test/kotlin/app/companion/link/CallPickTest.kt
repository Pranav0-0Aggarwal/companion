package app.companion.link

import android.telecom.Call
import app.companion.link.feature.MacInCall
import kotlin.test.Test
import kotlin.test.assertEquals

class CallPickTest {
    private fun pick(vararg s: Int) = MacInCall.pick(s.toList()) { it }

    @Test
    fun `ringing beats active beats dialing beats held`() {
        assertEquals(Call.STATE_RINGING, pick(Call.STATE_HOLDING, Call.STATE_DIALING, Call.STATE_ACTIVE, Call.STATE_RINGING))
        assertEquals(Call.STATE_ACTIVE, pick(Call.STATE_HOLDING, Call.STATE_DIALING, Call.STATE_ACTIVE))
        assertEquals(Call.STATE_DIALING, pick(Call.STATE_HOLDING, Call.STATE_DIALING))
        assertEquals(Call.STATE_SIMULATED_RINGING, pick(Call.STATE_ACTIVE, Call.STATE_SIMULATED_RINGING))
    }

    @Test
    fun `finished calls are never picked and state names are readable`() {
        assertEquals(null, pick(Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING))
        assertEquals("ringing", MacInCall.name(Call.STATE_SIMULATED_RINGING))
        assertEquals("active", MacInCall.name(Call.STATE_AUDIO_PROCESSING))
        assertEquals("held", MacInCall.name(Call.STATE_HOLDING))
        assertEquals("dialing", MacInCall.name(Call.STATE_CONNECTING))
    }
}
