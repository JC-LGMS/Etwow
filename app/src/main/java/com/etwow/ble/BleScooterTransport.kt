package com.etwow.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.etwow.data.Connection
import com.etwow.protocol.EtwowProtocol
import com.etwow.protocol.hex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Liaison BLE réelle (GT SE / SL / GTS).
 *
 * Une seule opération GATT à la fois : les écritures sont mises en file et avancées par les
 * callbacks. Un chien de garde évite qu'une opération sans callback ne bloque la file.
 */
class BleScooterTransport(
    private val context: Context,
    private val listener: ScooterTransport.Listener,
) : ScooterTransport {

    private companion object {
        const val NAME_ETWOW = "E-TWOW"
        const val NAME_GTSPORT = "GTSport"
        const val OP_TIMEOUT_MS = 4_000L
        const val RETRY_MAX_MS = 30_000L
        /** Scan rapide au démarrage, puis économique : une trottinette éteinte ne doit pas vider la batterie. */
        const val SCAN_FAST_MS = 30_000L

        val SERVICE_ETWOW: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val SERVICE_GTSPORT: UUID = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** Les appareils du nouveau protocole s'annoncent avec ces préfixes (app officielle). */
        fun isScooterName(name: String): Boolean =
            name.contains(NAME_ETWOW) ||
                name.contains(NAME_GTSPORT) ||
                EtwowProtocol.NEW_PROTOCOL_NAMES.any { name.startsWith(it) }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val queue = ArrayDeque<(BluetoothGatt) -> Unit>()
    private val lock = Any()

    private var gatt: BluetoothGatt? = null
    @Volatile
    private var writeChar: BluetoothGattCharacteristic? = null
    private var opBusy = false
    private var opGeneration = 0
    @Volatile
    private var scanning = false
    private var retryJob: Job? = null
    private var scanModeJob: Job? = null
    private var retryDelay = 1_000L

    private val adapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    override fun start() {
        retryDelay = 1_000L
        registerBluetoothState()
        scan()
    }

    override fun stop() {
        retryJob?.cancel()
        retryJob = null
        unregisterBluetoothState()
        stopScan()
        closeGatt()
        scope.cancel()
        listener.onConnection(Connection.Idle, null, null)
    }

    /**
     * Le Bluetooth du téléphone peut être éteint au démarrage du service (au boot, l'adaptateur
     * n'est pas encore prêt) ou rallumé plus tard : sans ça, le service restait hors ligne.
     */
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> {
                    retryDelay = 1_000L
                    retryJob?.cancel()
                    scan()
                }

                BluetoothAdapter.STATE_OFF -> {
                    stopScan()
                    listener.onConnection(Connection.Offline, null, null)
                    listener.onLog("BLE : Bluetooth désactivé")
                }
            }
        }
    }

    private var stateRegistered = false

    private fun registerBluetoothState() {
        if (stateRegistered) return
        ContextCompat.registerReceiver(
            context,
            bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        stateRegistered = true
    }

    private fun unregisterBluetoothState() {
        if (!stateRegistered) return
        stateRegistered = false
        try {
            context.unregisterReceiver(bluetoothStateReceiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    override fun write(frame: ByteArray) {
        val char = writeChar
        if (char == null) {
            listener.onLog("BLE : pas de caractéristique d'écriture, trame ignorée")
            return
        }
        enqueue { g ->
            listener.onLog("→ ${frame.hex()}")
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(char, frame, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                char.value = frame
                @Suppress("DEPRECATION")
                g.writeCharacteristic(char)
            }
        }
    }

    private fun scan() {
        val adapter = adapter
        if (adapter == null) {
            listener.onConnection(Connection.Offline, null, null)
            listener.onLog("BLE : adaptateur absent")
            scheduleRetry()
            return
        }
        if (!hasScanPermission()) {
            listener.onConnection(Connection.Offline, null, null)
            listener.onLog("BLE : permission de scan manquante")
            return
        }
        if (!adapter.isEnabled) {
            // Pas de nouvelle tentative ici : le récepteur relance le scan sur STATE_ON.
            listener.onConnection(Connection.Offline, null, null)
            listener.onLog("BLE : Bluetooth désactivé")
            return
        }
        synchronized(lock) {
            if (scanning) return
            scanning = true
        }
        listener.onConnection(Connection.Connecting, null, null)
        // Sans filtre de service : un modèle récent peut n'annoncer que son nom. On trie dans le
        // callback. Rapide 30 s, puis économique.
        startScan(ScanSettings.SCAN_MODE_LOW_LATENCY)
        scanModeJob?.cancel()
        scanModeJob = scope.launch {
            delay(SCAN_FAST_MS)
            if (!stopScan()) return@launch
            synchronized(lock) { scanning = true }
            startScan(ScanSettings.SCAN_MODE_LOW_POWER)
        }
    }

    private fun startScan(mode: Int) {
        try {
            adapter?.bluetoothLeScanner
                ?.startScan(null, ScanSettings.Builder().setScanMode(mode).build(), scanCallback)
                ?: failScan("scanner indisponible")
        } catch (e: SecurityException) {
            failScan("scan refusé (${e.message})")
        }
    }

    private fun failScan(reason: String) {
        synchronized(lock) { scanning = false }
        listener.onConnection(Connection.Offline, null, null)
        listener.onLog("BLE : $reason")
        scheduleRetry()
    }

    private fun stopScan(): Boolean {
        synchronized(lock) {
            if (!scanning) return false
            scanning = false
        }
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {
        }
        return true
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = result.scanRecord?.deviceName
                ?: try {
                    device.name
                } catch (_: SecurityException) {
                    null
                }
            if (!isScooter(result, name)) return
            stopScan()
            listener.onLog("BLE : trouvé ${name ?: device.address}")
            connect(device)
        }

        override fun onScanFailed(errorCode: Int) {
            failScan("échec du scan ($errorCode)")
        }
    }

    /** Nom annoncé ou UUID de service : un modèle récent peut n'annoncer que son nom. */
    private fun isScooter(result: ScanResult, name: String?): Boolean {
        if (name != null && isScooterName(name)) return true
        val uuids = result.scanRecord?.serviceUuids ?: return false
        return uuids.any { it.uuid == SERVICE_ETWOW || it.uuid == SERVICE_GTSPORT }
    }

    private fun connect(device: BluetoothDevice) {
        closeGatt()
        val dropped = synchronized(lock) {
            val count = queue.size
            writeChar = null
            queue.clear()
            opBusy = false
            count
        }
        if (dropped > 0) listener.onLog("BLE : $dropped opération(s) abandonnée(s)")
        try {
            val g = if (Build.VERSION.SDK_INT >= 26) {
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(context, false, callback)
            }
            gatt = g
            if (g == null) {
                // Sans GATT, aucun callback n'arrivera : sans ce garde-fou, « Connexion… » restait à vie.
                listener.onLog("BLE : connexion impossible (GATT indisponible)")
                scheduleRetry()
            }
        } catch (e: SecurityException) {
            listener.onLog("BLE : connexion refusée (${e.message})")
            scheduleRetry()
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        listener.onLog("BLE : connexion status $status")
                        closeGatt()
                        scheduleRetry()
                        return
                    }
                    retryDelay = 1_000L
                    listener.onLog("BLE : connecté, découverte des services")
                    try {
                        g.discoverServices()
                    } catch (_: SecurityException) {
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    listener.onLog("BLE : déconnecté (status $status)")
                    closeGatt()
                    listener.onConnection(Connection.Offline, null, null)
                    scheduleRetry()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onLog("BLE : découverte échouée ($status)")
                closeGatt()
                scheduleRetry()
                return
            }
            val pair = findCharacteristics(g)
            if (pair == null) {
                listener.onLog("BLE : caractéristiques ETWOW absentes")
                closeGatt()
                listener.onConnection(Connection.Offline, null, null)
                scheduleRetry()
                return
            }
            writeChar = pair.second
            enableNotifications(g, pair.first)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            finishOp()
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val name = try {
                    g.device.name
                } catch (_: SecurityException) {
                    null
                }
                listener.onConnection(Connection.Connected, name ?: "E-TWOW", g.device.address)
            } else {
                listener.onLog("BLE : notifications refusées ($status)")
                closeGatt()
                listener.onConnection(Connection.Offline, null, null)
                scheduleRetry()
            }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            listener.onNotification(value)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            characteristic.value?.let { listener.onNotification(it) }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) listener.onLog("BLE : écriture status $status")
            finishOp()
        }
    }

    private fun findCharacteristics(g: BluetoothGatt): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic>? {
        for (serviceUuid in listOf(SERVICE_ETWOW, SERVICE_GTSPORT)) {
            val service = g.getService(serviceUuid) ?: continue
            val chars = service.characteristics
            val read = chars.firstOrNull {
                val id = it.uuid.toString()
                id.startsWith("0000ffe1") || id.startsWith("0000ff01") || id.startsWith("0000ff04")
            } ?: chars.firstOrNull {
                it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
            }
            val write = chars.firstOrNull {
                it.uuid.toString().startsWith("0000ff02")
            } ?: chars.firstOrNull {
                it.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            }
            if (read != null && write != null) return read to write
        }
        return null
    }

    private fun enableNotifications(g: BluetoothGatt, char: BluetoothGattCharacteristic) {
        try {
            g.setCharacteristicNotification(char, true)
            val descriptor = char.getDescriptor(CCCD)
            if (descriptor == null) {
                listener.onLog("BLE : descripteur CCCD absent")
                closeGatt()
                listener.onConnection(Connection.Offline, null, null)
                scheduleRetry()
                return
            }
            enqueue {
                if (Build.VERSION.SDK_INT >= 33) {
                    g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(descriptor)
                }
            }
        } catch (e: SecurityException) {
            listener.onLog("BLE : notifications refusées (${e.message})")
            closeGatt()
            listener.onConnection(Connection.Offline, null, null)
            scheduleRetry()
        }
    }

    private fun enqueue(op: (BluetoothGatt) -> Unit) {
        synchronized(lock) { queue.addLast(op) }
        pump()
    }

    private fun pump() {
        // Pioche sous verrou : write() vient du thread principal, les callbacks GATT d'un autre.
        val (g, op, generation) = synchronized(lock) {
            if (opBusy) return
            val g = gatt ?: return
            val op = queue.removeFirstOrNull() ?: return
            opBusy = true
            Triple(g, op, ++opGeneration)
        }
        try {
            op(g)
        } catch (e: SecurityException) {
            listener.onLog("BLE : opération refusée (${e.message})")
            finishOp()
            return
        }
        scope.launch {
            delay(OP_TIMEOUT_MS)
            val abandoned = synchronized(lock) {
                if (generation == opGeneration) {
                    opBusy = false
                    true
                } else {
                    false
                }
            }
            if (abandoned) {
                listener.onLog("BLE : délai dépassé, opération abandonnée")
                pump()
            }
        }
    }

    private fun finishOp() {
        synchronized(lock) {
            opGeneration++
            opBusy = false
        }
        pump()
    }

    private fun scheduleRetry() {
        retryJob?.cancel()
        val delayMs = retryDelay
        retryDelay = (retryDelay * 2).coerceAtMost(RETRY_MAX_MS)
        retryJob = scope.launch {
            delay(delayMs)
            if (isActive) scan()
        }
    }

    private fun closeGatt() {
        val g = synchronized(lock) {
            writeChar = null
            gatt.also { gatt = null }
        }
        try {
            g?.disconnect()
            g?.close()
        } catch (_: SecurityException) {
        }
    }

    private fun hasScanPermission(): Boolean {
        val needed = if (Build.VERSION.SDK_INT >= 31) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(context, needed) == PackageManager.PERMISSION_GRANTED
    }
}
