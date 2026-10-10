package app.companion.link

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import app.companion.core.LinkFrames
import app.companion.core.LinkReassembler
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class LinkGatt(
    private val c: Context,
    private val scope: CoroutineScope,
    private val up: () -> Unit,
    private val down: () -> Unit,
    private val got: (ByteArray) -> Unit,
) {
    private val bm = c.getSystemService(BluetoothManager::class.java)
    private var server: BluetoothGattServer? = null
    private var tx: BluetoothGattCharacteristic? = null
    private var dev: BluetoothDevice? = null
    private var prio: BluetoothGatt? = null
    private var calm: Job? = null
    private var rx = LinkReassembler()
    private val out = ArrayDeque<ByteArray>()
    private var mtu = MTU
    private var seq = 0
    private var subscribed = false
    private var busy = false
    private var advertising = false

    val connected get() = dev != null

    fun open() {
        if (server != null) return
        val s = bm?.openGattServer(c, callback) ?: return
        val write = BluetoothGattCharacteristic(RX, BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_WRITE)
        val notify = BluetoothGattCharacteristic(TX, BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0).apply {
            addDescriptor(BluetoothGattDescriptor(CCCD, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))
        }
        s.addService(BluetoothGattService(SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply { addCharacteristic(write); addCharacteristic(notify) })
        server = s
        tx = notify
    }

    fun close() {
        advertise(false)
        dev?.let { server?.cancelConnection(it) }
        server?.close()
        server = null
        tx = null
        if (dev != null) drop()
    }

    fun advertise(on: Boolean) {
        val a = bm?.adapter?.bluetoothLeAdvertiser
        if (on && !advertising && a != null) {
            advertising = true
            a.startAdvertising(SETTINGS, DATA, advert)
        } else if (!on && advertising) {
            advertising = false
            a?.stopAdvertising(advert)
        }
    }

    fun disconnect() {
        dev?.let { server?.cancelConnection(it) }
    }

    fun send(msg: ByteArray) {
        if (dev == null) return
        LinkFrames.split(msg, seq++ and 63, mtu - 4).forEach(out::add)
        pump()
        if (msg.size > BULK) bulk()
    }

    fun calmDown() {
        val d = dev ?: return
        prio = d.connectGatt(
            c, false,
            object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, state: Int) {
                    if (state == BluetoothProfile.STATE_CONNECTED) g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER)
                }
            },
            BluetoothDevice.TRANSPORT_LE,
        )
    }

    private fun bulk() {
        prio?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED)
        calm?.cancel()
        calm = scope.launch {
            delay(CALM)
            prio?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER)
        }
    }

    private fun pump() {
        val d = dev ?: return
        val t = tx ?: return
        if (busy || !subscribed) return
        val f = out.removeFirstOrNull() ?: return
        busy = true
        if (server?.notifyCharacteristicChanged(d, t, false, f) != BluetoothStatusCodes.SUCCESS) {
            busy = false
            out.clear()
        }
    }

    private fun drop() {
        dev = null
        subscribed = false
        busy = false
        out.clear()
        calm?.cancel()
        prio?.close()
        prio = null
        down()
    }

    private fun link(d: BluetoothDevice, state: Int) {
        if (state == BluetoothProfile.STATE_CONNECTED) {
            if (dev != null) {
                if (dev != d) server?.cancelConnection(d)
                return
            }
            dev = d
            mtu = MTU
            seq = 0
            rx = LinkReassembler()
            up()
        } else if (d == dev) {
            drop()
        }
    }

    private val callback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(d: BluetoothDevice, status: Int, state: Int) {
            scope.launch { link(d, state) }
        }

        override fun onMtuChanged(d: BluetoothDevice, m: Int) {
            scope.launch { if (d == dev) mtu = m }
        }

        override fun onCharacteristicWriteRequest(d: BluetoothDevice, id: Int, ch: BluetoothGattCharacteristic, prepared: Boolean, response: Boolean, offset: Int, value: ByteArray) {
            scope.launch {
                val ok = d == dev && ch.uuid == RX && !prepared && offset == 0
                if (response) server?.sendResponse(d, id, if (ok) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE, 0, null)
                if (ok) rx.feed(value)?.let(got)
            }
        }

        override fun onDescriptorWriteRequest(d: BluetoothDevice, id: Int, desc: BluetoothGattDescriptor, prepared: Boolean, response: Boolean, offset: Int, value: ByteArray) {
            scope.launch {
                val ok = d == dev && desc.uuid == CCCD && !prepared && offset == 0
                if (ok) {
                    subscribed = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    pump()
                }
                if (response) server?.sendResponse(d, id, if (ok) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }

        override fun onDescriptorReadRequest(d: BluetoothDevice, id: Int, offset: Int, desc: BluetoothGattDescriptor) {
            scope.launch {
                val v = if (subscribed) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                server?.sendResponse(d, id, BluetoothGatt.GATT_SUCCESS, 0, v)
            }
        }

        override fun onNotificationSent(d: BluetoothDevice, status: Int) {
            scope.launch {
                busy = false
                pump()
            }
        }
    }

    private val advert = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            scope.launch { advertising = false }
        }
    }

    private companion object {
        val SERVICE: UUID = UUID.fromString("8A15703F-3520-40A6-930A-56CDD28BBB5C")
        val RX: UUID = UUID.fromString("3F1D68DF-4B0D-4A88-AE3C-8A18457E97A9")
        val TX: UUID = UUID.fromString("C0AAFA1B-E57D-4619-A1D7-FFB514C1AC69")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val SETTINGS: AdvertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val DATA: AdvertiseData = AdvertiseData.Builder().setIncludeDeviceName(false).setIncludeTxPowerLevel(false).addServiceUuid(ParcelUuid(SERVICE)).build()
        const val MTU = 23
        const val BULK = 1024
        const val CALM = 5_000L
    }
}
