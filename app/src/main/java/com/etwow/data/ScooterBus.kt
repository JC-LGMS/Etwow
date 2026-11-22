package com.etwow.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Connection { Idle, Connecting, Connected, Offline }

/** Dernier état connu, partagé par le service, l'UI et le widget (même processus). */
data class ScooterSnapshot(
    val connection: Connection = Connection.Idle,
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val speedKmh: Int? = null,
    val batteryPercent: Int? = null,
    val tripKm: Double? = null,
    val odoKm: Int? = null,
    val locked: Boolean? = null,
    val lightOn: Boolean? = null,
    val mode: Int? = null,
    val statusByte: Int? = null,
    val pending: String? = null,
    val simulator: Boolean = false,
    val autoLight: Boolean = false,
    val solarSummary: String? = null,
    val logs: List<String> = emptyList(),
)

object ScooterBus {
    private val _state = MutableStateFlow(ScooterSnapshot())
    val state: StateFlow<ScooterSnapshot> = _state

    fun update(block: (ScooterSnapshot) -> ScooterSnapshot) {
        _state.value = block(_state.value)
    }

    fun log(line: String) = update { it.copy(logs = (it.logs + line).takeLast(200)) }
}
