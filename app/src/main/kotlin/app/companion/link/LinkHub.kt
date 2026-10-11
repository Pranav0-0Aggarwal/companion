package app.companion.link

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.service.notification.StatusBarNotification
import app.companion.Services
import app.companion.core.Json
import app.companion.core.LinkCrypto
import app.companion.core.LinkMsg
import app.companion.core.LinkPair
import app.companion.core.LinkSession
import app.companion.link.bulk.Bulk
import app.companion.ui.has
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

enum class Stage { Off, Unpaired, NoPermission, BluetoothOff, Waiting, Linking, Connected }

data class LinkState(val stage: Stage = Stage.Off, val name: String? = null, val shares: Set<String> = emptySet(), val apps: Set<String> = emptySet())

@OptIn(ExperimentalCoroutinesApi::class)
class LinkHub(private val app: Application, sl: Services) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val gatt = LinkGatt(app, scope, ::up, ::down, ::got)
    private val events = LinkEvents(app, sl, { LinkStore.shares(app) }, ::push)
    private val cmds = LinkCmds(app, sl, { LinkStore.shares(app) }, { okm })
    private var session: LinkSession? = null
    @Volatile private var authed = false
    private var conn: CoroutineScope? = null
    private var wait: Job? = null
    private var radio: BroadcastReceiver? = null

    val okm: ByteArray? get() = if (authed) session?.okm else null

    val state = MutableStateFlow(LinkState())
    val pending = MutableStateFlow<LinkPair?>(null)

    fun start() {
        if (radio == null) {
            radio = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) = refresh()
            }.also { app.registerReceiver(it, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), Context.RECEIVER_NOT_EXPORTED) }
        }
        refresh()
    }

    fun refresh() {
        scope.launch { reconcile() }
    }

    fun enable(on: Boolean) {
        LinkStore.setOn(app, on)
        start()
    }

    fun pair(p: LinkPair) {
        LinkStore.setPair(app, p)
        scope.launch { gatt.disconnect() }
        start()
    }

    fun unpair() {
        LinkStore.unpair(app)
        refresh()
    }

    fun share(k: String, on: Boolean) {
        LinkStore.setShare(app, k, on)
        scope.launch {
            if (authed) {
                send(LinkMsg.shareEvent(LinkStore.shares(app)))
                events.sync()
                LinkFeatures.all.filter { it.key == k }.forEach { f ->
                    f.stop()
                    if (on) conn?.let { c -> runCatching { f.start(c, ::push) } }
                }
            }
            reconcile()
        }
    }

    fun allow(pkg: String, on: Boolean) {
        LinkStore.setApp(app, pkg, on)
        refresh()
    }

    fun focus(on: Boolean) {
        scope.launch { events.focus(on) }
    }

    fun notif(sbn: StatusBarNotification, importance: () -> Int) {
        if (authed) events.notif(sbn, importance)
    }

    fun stop() {
        radio?.let { runCatching { app.unregisterReceiver(it) } }
        radio = null
        scope.launch {
            end()
            gatt.close()
        }
    }

    private fun stage(pair: LinkPair?) = when {
        !LinkStore.on(app) -> Stage.Off
        pair == null -> Stage.Unpaired
        !app.has(Manifest.permission.BLUETOOTH_CONNECT) || !app.has(Manifest.permission.BLUETOOTH_ADVERTISE) -> Stage.NoPermission
        app.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled != true -> Stage.BluetoothOff
        authed -> Stage.Connected
        gatt.connected -> Stage.Linking
        else -> Stage.Waiting
    }

    private fun reconcile() {
        val pair = LinkStore.pair(app)
        val s = stage(pair)
        state.value = LinkState(s, pair?.name, LinkStore.shares(app), LinkStore.apps(app))
        if (s == Stage.Waiting || s == Stage.Linking || s == Stage.Connected) {
            gatt.open()
            gatt.advertise(!gatt.connected)
        } else {
            end()
            gatt.close()
        }
    }

    private fun up() {
        end()
        conn = CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext[Job]))
        timeout()
        gatt.advertise(false)
        reconcile()
    }

    private fun down() {
        end()
        reconcile()
    }

    private fun end() {
        conn?.coroutineContext?.get(Job)?.cancel()
        conn = null
        wait?.cancel()
        events.stop()
        Bulk.closeAll()
        LinkFeatures.all.forEach(LinkFeature::stop)
        session = null
        authed = false
    }

    private fun timeout() {
        wait?.cancel()
        wait = scope.launch {
            delay(HANDSHAKE)
            gatt.disconnect()
        }
    }

    private fun got(m: ByteArray) {
        when (m.firstOrNull()) {
            LinkMsg.PLAIN -> hello(m)
            LinkSession.SEALED -> sealed(m)
            else -> Unit
        }
    }

    private fun hello(m: ByteArray) {
        if (session != null) return gatt.disconnect()
        val j = runCatching { Json.obj(m.decodeToString(1)) }.getOrNull() ?: return
        val nm = runCatching { LinkCrypto.unb64(j["nm"] as String) }.getOrNull()
        val pair = LinkStore.pair(app)
        if (j["t"] != "hello" || (j["v"] as? Number)?.toInt() != 1 || nm?.size != 16 || pair == null) return gatt.disconnect()
        val keys = LinkStore.keys(app)
        val np = LinkCrypto.random(16)
        session = LinkCrypto.session(keys.priv, pair.mac, pair.secret, nm, np)
        gatt.send(LinkMsg.plain(Json.write(LinkMsg.hello(np, keys.pub))))
        timeout()
    }

    private fun sealed(m: ByteArray) {
        val s = session ?: return gatt.disconnect()
        val plain = try {
            s.open(m)
        } catch (_: Exception) {
            return gatt.disconnect()
        }
        val j = runCatching { Json.obj(plain.decodeToString()) }.getOrNull() ?: return
        if (authed) request(j) else if (j["t"] == "auth") accept() else gatt.disconnect()
    }

    private fun accept() {
        authed = true
        wait?.cancel()
        send(LinkMsg.ok(Settings.Global.getString(app.contentResolver, Settings.Global.DEVICE_NAME) ?: Build.MODEL, LinkStore.shares(app)))
        conn?.let { c ->
            events.start(c)
            val on = LinkStore.shares(app)
            LinkFeatures.all.forEach { if (it.key in on) runCatching { it.start(c, ::push) } }
        }
        gatt.calmDown()
        reconcile()
    }

    private fun request(j: Obj) {
        val t = j["t"] as? String ?: return
        val id = (j["id"] as? Number)?.toLong() ?: return
        conn?.launch {
            val res = try {
                mapOf("t" to "res", "id" to id, "ok" to true, "data" to cmds.run(t, j))
            } catch (e: CancellationException) {
                throw e
            } catch (e: LinkOff) {
                fail(id, "off")
            } catch (e: LinkBad) {
                fail(id, e.message ?: "bad")
            } catch (e: Exception) {
                fail(id, "failed")
            }
            send(res)
        }
    }

    private fun fail(id: Long, why: String) = mapOf("t" to "res", "id" to id, "ok" to false, "err" to why)

    fun push(o: Obj) {
        scope.launch { send(o) }
    }

    private fun send(o: Obj) {
        val s = session ?: return
        gatt.send(s.seal(Json.write(o).encodeToByteArray()))
    }

    private companion object {
        const val HANDSHAKE = 10_000L
    }
}
