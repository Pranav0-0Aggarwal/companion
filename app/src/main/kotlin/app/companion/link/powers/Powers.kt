package app.companion.link.powers

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import app.companion.R
import app.companion.core.LinkMsg
import app.companion.link.Obj
import app.companion.system.Notices
import app.companion.ui.has
import java.net.Inet4Address
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class Powers(private val app: Application, private val push: (Obj) -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val main = Handler(Looper.getMainLooper())
    private val cm = app.getSystemService(ConnectivityManager::class.java)
    private val wifi = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = onLinked()
    }
    private val unwatch = Runnable { unwatch() }
    private var timer: Job? = null
    private var link: Job? = null
    private var obs: ContentObserver? = null
    val bridge = BridgeClient(scope)

    fun granted() = app.checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED

    fun track(on: Boolean) {
        runCatching { cm.unregisterNetworkCallback(wifi) }
        if (on) cm.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), wifi)
    }

    @Synchronized
    fun onLinked() {
        if (link?.isActive == true) return
        link = scope.launch { relink() }
    }

    private suspend fun relink() {
        if (timer?.isActive == true) return
        if (bridge.up.value || bridge.connect()) return push(LinkMsg.bridge(true))
        if (!granted()) return push(LinkMsg.bridge(false, "setup"))
        val ip = wifiIp() ?: return
        adbWifi(true)
        push(LinkMsg.bridgeWant(ip))
        timer = scope.launch {
            delay(LAUNCH)
            adbWifi(false)
            push(LinkMsg.bridge(false, "launch"))
        }
    }

    private fun wifiIp(): String? {
        val n = cm.activeNetwork ?: return null
        if (cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return null
        return cm.getLinkProperties(n)?.linkAddresses?.firstOrNull { it.address is Inet4Address }?.address?.hostAddress
    }

    suspend fun started(): Obj {
        timer?.cancel()
        val up = bridge.connect() && runCatching { bridge.cmd("caps") }.isSuccess
        adbWifi(false)
        if (up) push(LinkMsg.bridge(true)) else bridge.close()
        return mapOf("up" to up)
    }

    @Synchronized
    fun adbWifi(on: Boolean) {
        if (!granted()) return
        main.removeCallbacks(unwatch)
        unwatch()
        if (on) {
            obs = object : ContentObserver(main) {
                override fun onChange(self: Boolean) {
                    if (Settings.Global.getInt(app.contentResolver, ADB, 0) == 0) untrusted()
                }
            }.also { app.contentResolver.registerContentObserver(Settings.Global.getUriFor(ADB), false, it) }
            main.postDelayed(unwatch, WATCH)
        }
        Settings.Global.putInt(app.contentResolver, ADB, if (on) 1 else 0)
    }

    @Synchronized
    private fun unwatch() {
        obs?.let(app.contentResolver::unregisterContentObserver)
        obs = null
    }

    private fun untrusted() {
        timer?.cancel()
        main.removeCallbacks(unwatch)
        unwatch()
        push(LinkMsg.bridge(false, "network"))
        if (!app.has(Manifest.permission.POST_NOTIFICATIONS)) return
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHAN, "Phone powers", NotificationManager.IMPORTANCE_DEFAULT))
        nm.notify(
            ID,
            Notification.Builder(app, CHAN)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentTitle("Allow wireless debugging on this Wi-Fi once (Always allow)")
                .setAutoCancel(true)
                .setContentIntent(Notices.open(app))
                .build(),
        )
    }

    private companion object {
        const val PERM = "android.permission.WRITE_SECURE_SETTINGS"
        const val ADB = "adb_wifi_enabled"
        const val CHAN = "powers"
        const val ID = 0x50575253
        const val LAUNCH = 30_000L
        const val WATCH = 2_000L
    }
}
