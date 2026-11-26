package com.etwow.data

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class Connection { Idle, Connecting, Connected, Offline }

/** Dernier état connu, partagé par le service, l'UI et le widget (même processus). */
data class ScooterSnapshot(
    val connection: Connection = Connection.Idle,
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val speedKmh: Double? = null,
    val batteryPercent: Int? = null,
    val tripKm: Double? = null,
    val odoKm: Double? = null,
    val locked: Boolean? = null,
    val lightOn: Boolean? = null,
    val zeroStart: Boolean? = null,
    val driveMode: Int? = null,
    val mode: Int? = null,
    val statusByte: Int? = null,
    val pending: String? = null,
    val simulator: Boolean = false,
    val solarSummary: String? = null,
    val logs: List<String> = emptyList(),
)

object ScooterBus {
    private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")

    private val _state = MutableStateFlow(ScooterSnapshot())
    val state: StateFlow<ScooterSnapshot> = _state

    /** Atomique : service et callbacks BLE écrivent depuis des threads différents. */
    fun update(block: (ScooterSnapshot) -> ScooterSnapshot) {
        _state.update(block)
    }

    fun log(line: String) =
        update { it.copy(logs = (it.logs + "${LocalTime.now().format(TIME)} $line").takeLast(200)) }
}
