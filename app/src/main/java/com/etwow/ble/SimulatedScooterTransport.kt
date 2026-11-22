package com.etwow.ble

import com.etwow.data.Connection
import com.etwow.protocol.EtwowProtocol
import com.etwow.protocol.SpeedModes
import com.etwow.protocol.hex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Trottinette simulée : mêmes trames que le protocole, réponses aux commandes.
 * Seul moyen de vérifier l'application sur l'émulateur, qui n'a pas de Bluetooth.
 * [newProtocol] la fait parler le format de 20 octets des modèles récents.
 */
class SimulatedScooterTransport(
    private val listener: ScooterTransport.Listener,
    private val newProtocol: Boolean = false,
) : ScooterTransport {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null

    private var locked = false
    private var light = false
    private var zeroStart = false
    private var driveMode = 0
    private var mode = SpeedModes.L2_20
    private var battery = 82
    private var odoTenths = 12_400
    private var tripTenths = 0
    private var speed = 0

    override fun start() {
        job?.cancel()
        job = scope.launch {
            listener.onConnection(Connection.Connecting, null, null)
            delay(700)
            val name = if (newProtocol) "TK-SIM" else "E-TWOW SIM"
            listener.onConnection(Connection.Connected, name, "SIM-00:11:22:33:44:55")
            emitStatus()
            emitBattery()
            emitSpeed()
            emitDistance()

            var tick = 0
            while (isActive) {
                delay(1000)
                tick++
                // Vingt secondes à l'arrêt puis dix en mouvement : le temps de verrouiller à la main.
                val phase = tick % 30
                speed = when {
                    locked -> 0
                    phase < 20 -> 0
                    else -> ((phase - 19) * 3).coerceAtMost(25)
                }
                if (tick % 15 == 0) {
                    odoTenths += 1
                    tripTenths += 1
                }
                if (tick % 30 == 0 && battery > 0) battery -= 1
                emitSpeed()
                if (tick % 5 == 0) emitDistance()
                if (tick % 30 == 0) emitBattery()
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        listener.onConnection(Connection.Idle, null, null)
    }

    override fun write(frame: ByteArray) {
        listener.onLog("→ ${frame.hex()}")
        if (newProtocol) writeNew(frame) else writeOld(frame)
    }

    private fun writeOld(frame: ByteArray) {
        if (frame.size < 5) return
        val cmd = frame[1].toInt() and 0xFF
        val arg = frame[3].toInt() and 0xFF
        scope.launch {
            delay(150)
            when (cmd) {
                0x05 -> locked = arg == 1
                0x06 -> light = arg == 1
                0x02 -> mode = arg and 0x0F
                0x03 -> zeroStart = arg == 1
                0x09 -> {
                    tripTenths = 0
                    if (arg == 0) tripTenths = 0 else odoTenths = 0
                }
                0x18 -> listener.onLog("simulateur : klaxon")
                else -> listener.onLog("simulateur : commande inconnue ${frame.hex()}")
            }
            if (cmd in 0x02..0x06 || cmd == 0x09) emitStatus()
        }
    }

    private fun writeNew(frame: ByteArray) {
        if (frame.size != 20) return
        val opcode = frame[4].toInt() and 0xFF
        val action = frame[5].toInt() and 0xFF
        scope.launch {
            delay(150)
            when (opcode) {
                0x01 -> locked = action == 1
                0x03 -> light = action == 1
                0x08 -> mode = action and 0x03
                0x0E -> zeroStart = action == 1
                0x09 -> driveMode = action and 0x03
                0x0F -> {
                    tripTenths = 0
                    odoTenths = 0
                }
                0x12 -> listener.onLog("simulateur : klaxon")
                else -> listener.onLog("simulateur : commande inconnue ${frame.hex()}")
            }
            emitStatus()
            emitSpeed()
        }
    }

    /** Notification historique : type, données, puis somme des octets précédents. */
    private fun notify(vararg values: Int): ByteArray {
        val sum = values.sum() and 0xFF
        return ByteArray(values.size + 1) { index ->
            if (index < values.size) values[index].toByte() else sum.toByte()
        }
    }

    /** Notification du nouveau protocole : en-tête, opcode, données, XOR, 0x0D. */
    private fun notifyNew(opcode: Int, fill: (IntArray) -> Unit): ByteArray {
        val bytes = IntArray(20)
        bytes[0] = 0xAA
        bytes[1] = 0x55
        bytes[2] = 0x00
        bytes[3] = 0x0D
        bytes[4] = opcode
        fill(bytes)
        var xor = 0
        for (index in 0 until 18) xor = xor xor bytes[index]
        bytes[18] = xor
        bytes[19] = 0x0D
        return ByteArray(20) { bytes[it].toByte() }
    }

    private fun emitStatus() {
        if (newProtocol) {
            emit(notifyNew(0x0C) {
                val odo = odoTenths
                it[5] = (odo shr 24) and 0xFF
                it[6] = (odo shr 16) and 0xFF
                it[7] = (odo shr 8) and 0xFF
                it[8] = odo and 0xFF
                it[10] = when (driveMode) {
                    0 -> 0x08
                    1 -> 0x10
                    2 -> 0x20
                    else -> 0x04
                }
                it[11] = (if (light) 0x04 else 0) or (if (zeroStart) 0x80 else 0)
            })
        } else {
            emit(
                notify(
                    3,
                    (if (light) 0x10 else 0) or (if (locked) 0x20 else 0) or
                        (if (zeroStart) 0x80 else 0) or mode,
                    0,
                ),
            )
        }
    }

    private fun emitBattery() {
        if (newProtocol) emit(notifyNew(0x0B) { it[14] = battery }) else emit(notify(2, battery, 0))
    }

    private fun emitSpeed() {
        if (newProtocol) {
            emit(notifyNew(0x0B) {
                it[13] = speed
                it[14] = battery
                it[15] = (tripTenths shr 8) and 0xFF
                it[16] = tripTenths and 0xFF
                it[17] = if (locked) 0x01 else 0x00
            })
        } else {
            val tenths = speed * 10
            emit(notify(1, tenths and 0xFF, (tenths shr 8) and 0xFF))
        }
    }

    private fun emitDistance() {
        if (newProtocol) {
            emitStatus()
        } else {
            emit(notify(4, odoTenths and 0xFF, (odoTenths shr 8) and 0xFF))
        }
    }

    private fun emit(bytes: ByteArray) {
        // Le journal brut est écrit une seule fois, par ScooterService.
        listener.onNotification(bytes)
    }
}
