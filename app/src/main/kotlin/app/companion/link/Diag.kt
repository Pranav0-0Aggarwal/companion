package app.companion.link

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import app.companion.core.DiagLine
import app.companion.core.DiagRing
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

object Diag {
    private const val MAX = 128 * 1024
    private val ring = DiagRing()
    private val counts = ConcurrentHashMap<String, Long>()
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var app: Context? = null
    private var file: File? = null

    fun init(c: Context) {
        app = c.applicationContext
        file = File(c.filesDir, "diag.log")
    }

    fun log(s: String) {
        Log.w("CompanionLink", s)
        val l = DiagLine(System.currentTimeMillis(), pct(), s)
        ring.add(l)
        io.execute { append("${l.at} ${l.bat}% $s\n") }
    }

    fun add(k: String, n: Long = 1) {
        counts.merge(k, n, Long::plus)
    }

    fun snapshot(since: Long): Obj {
        val b = sticky()
        return mapOf(
            "log" to ring.since(since).map { mapOf("at" to it.at, "bat" to it.bat, "m" to it.m) },
            "stats" to counts + mapOf(
                "cpu_ms" to Process.getElapsedCpuTime(),
                "up_ms" to SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime(),
                "bat" to pct(),
                "temp_c10" to b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0),
                "charging" to ((b?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0),
                "v" to app?.let { it.packageManager.getPackageInfo(it.packageName, 0).versionName },
            ),
        )
    }

    private fun sticky(): Intent? = app?.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun pct() = app?.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

    private fun append(line: String) = runCatching {
        val f = file ?: return@runCatching
        f.appendText(line)
        if (f.length() > MAX) {
            val t = f.readText()
            f.writeText(t.substring(t.indexOf('\n', t.length / 2) + 1))
        }
    }
}
