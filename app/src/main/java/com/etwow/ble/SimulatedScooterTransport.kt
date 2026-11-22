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
 */
class SimulatedScooterTransport(private val listener: ScooterTransport.Listener) : ScooterTransport {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null

    private var locked = false
    private var light = false
    private var mode = SpeedModes.L2_20
    private var battery = 82
    private var trip = 0
    private var odo = 1240
    private var speed = 0

    override fun start() {
        job?.cancel()
        job = scope.launch {
            listener.onConnection(Connection.Connecting, null, null)
            delay(700)
            listener.onConnection(Connection.Connected, "E-TWOW SIM", "SIM-00:11:22:33:44:55")
            emit(statusFrame())
            emit(byteArrayOf(2, battery.toByte()))
            emit(speedFrame())
            emit(tripFrame())
            emit(odoFrame())

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
                if (tick % 5 == 0) trip += 1
                if (tick % 5 == 0) odo += 1
                if (tick % 30 == 0 && battery > 0) battery -= 1
                emit(speedFrame())
                if (tick % 5 == 0) {
                    emit(tripFrame())
                    emit(odoFrame())
                }
                if (tick % 30 == 0) emit(byteArrayOf(2, battery.toByte()))
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        listener.onConnection(Connection.Idle, null, null)
    }

    override fun write(frame: ByteArray) {
        if (frame.size < 5) return
        listener.onLog("→ ${frame.hex()}")
        val cmd = frame[1].toInt() and 0xFF
        val arg = frame[3].toInt() and 0xFF
        scope.launch {
            delay(150)
            when (cmd) {
                0x05 -> locked = arg == 1
                0x06 -> light = arg == 1
                0x02 -> mode = arg and 0x0F
                else -> listener.onLog("simulateur : commande inconnue ${frame.hex()}")
            }
            if (cmd in 0x02..0x06) emit(statusFrame())
        }
    }

    private fun statusFrame(): ByteArray = byteArrayOf(
        3,
        (0x40 or (if (light) 0x10 else 0) or (if (locked) 0x20 else 0) or mode).toByte(),
    )

    private fun speedFrame(): ByteArray = byteArrayOf(1, (speed and 0xFF).toByte(), 0)

    private fun tripFrame(): ByteArray =
        byteArrayOf(4, ((trip shr 8) and 0xFF).toByte(), (trip and 0xFF).toByte())

    private fun odoFrame(): ByteArray = byteArrayOf(
        5,
        0,
        0,
        ((odo shr 16) and 0xFF).toByte(),
        ((odo shr 8) and 0xFF).toByte(),
        (odo and 0xFF).toByte(),
    )

    private fun emit(bytes: ByteArray) {
        // Le journal brut est écrit une seule fois, par ScooterService.
        listener.onNotification(bytes)
    }
}
