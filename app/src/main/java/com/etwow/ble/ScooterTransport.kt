package com.etwow.ble

import com.etwow.data.Connection

/**
 * Liaison avec la trottinette. Deux implémentations : BLE réel et simulateur.
 * Le transport ne décode rien : il remonte les notifications brutes.
 */
interface ScooterTransport {
    interface Listener {
        fun onConnection(state: Connection, name: String?, address: String?)
        fun onNotification(bytes: ByteArray)
        fun onLog(line: String)
    }

    fun start()
    fun stop()

    /** Ignoré si la liaison n'est pas prête ; une écriture à la fois côté service. */
    fun write(frame: ByteArray)
}
