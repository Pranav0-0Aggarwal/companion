package app.companion.bridge

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import app.companion.core.BridgeWire
import app.companion.core.CallRestore
import kotlin.concurrent.thread

class CallAudio(private val ctx: Context, private val out: (Int, ByteArray) -> Unit) {
    private val am by lazy { Shell.audio(ctx) }
    private var rec: AudioRecord? = null
    @Volatile private var trk: AudioTrack? = null
    private var hold: CallRestore? = null
    private var pump: Thread? = null
    private var cb: TelephonyCallback? = null

    @Synchronized
    fun start() {
        check(hold == null) { "busy" }
        check(Shell.sys<Boolean>(am, "isPstnCallAudioInterceptable")) { "nointercept" }
        try {
            val r = Shell.sys<AudioRecord>(am, "getCallDownlinkExtractionAudioRecord", fmt(AudioFormat.CHANNEL_IN_MONO))
            rec = r
            val t = Shell.sys<AudioTrack>(am, "getCallUplinkInjectionAudioTrack", fmt(AudioFormat.CHANNEL_OUT_MONO))
            trk = t
            hold = CallRestore(am.getStreamVolume(VOICE), am.isMicrophoneMute)
            am.setStreamVolume(VOICE, am.getStreamMinVolume(VOICE), 0)
            am.isMicrophoneMute = true
            t.play()
            r.startRecording()
            pump = thread(isDaemon = true) { pump(r) }
            val c = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    if (state == TelephonyManager.CALL_STATE_IDLE) stop()
                }
            }
            cb = c
            runCatching { ctx.getSystemService(TelephonyManager::class.java).registerTelephonyCallback(ctx.mainExecutor, c) }
        } catch (e: Throwable) {
            stop()
            throw e
        }
    }

    @Synchronized
    fun stop() {
        cb?.let { c -> runCatching { ctx.getSystemService(TelephonyManager::class.java).unregisterTelephonyCallback(c) } }
        cb = null
        rec?.let { runCatching(it::stop) }
        pump?.join(JOIN)
        pump = null
        rec?.let { runCatching(it::release) }
        rec = null
        trk?.let { runCatching(it::stop); runCatching(it::release) }
        trk = null
        hold?.take()?.let { (v, m) ->
            runCatching { am.setStreamVolume(VOICE, v, 0) }
            runCatching { am.isMicrophoneMute = m }
        }
        hold = null
    }

    fun play(b: ByteArray) {
        if (b.size == 2 + FRAME) trk?.write(b, 2, FRAME, AudioTrack.WRITE_NON_BLOCKING)
    }

    private fun pump(r: AudioRecord) {
        val b = ByteArray(2 + FRAME)
        var seq = 0
        try {
            while (r.read(b, 2, FRAME, AudioRecord.READ_BLOCKING) == FRAME) {
                b[0] = (seq shr 8).toByte()
                b[1] = seq.toByte()
                seq = (seq + 1) and 0xFFFF
                out(BridgeWire.AUDIO, b)
            }
        } catch (_: Exception) {
        }
    }

    private fun fmt(mask: Int) = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(mask).build()

    private companion object {
        const val VOICE = AudioManager.STREAM_VOICE_CALL
        const val RATE = 16_000
        const val FRAME = 640
        const val JOIN = 300L
    }
}
