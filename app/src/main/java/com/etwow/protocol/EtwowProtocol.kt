package com.etwow.protocol

/**
 * Codec du protocole ETWOW (GT SE / SL / GTS).
 *
 * Commande : `55 <cmd> 05 <arg> <somme>`, somme de tous les octets, 0x55 inclus.
 *
 * Notifications : 4 octets sur les machines connues — `type`, valeur **int16 petit-boutiste**
 * (octets 1 et 2), puis somme de contrôle (somme des octets précédents). Règles reprises du
 * parseur de l'application officielle, documenté dans Laufbursche42/etwow-unlock :
 *   type 1 → vitesse / 10, type 2 → batterie brute, type 3 → statut + cran de limite,
 *   type 4 → distance / 10.
 * Vérifié sur une GTS réelle le 22/11/2026 : `01 00 00 | 01` (0 km/h), `02 64 00 | 66` (100 %),
 * `03 10 00 | 13` (phare allumé), `04 67 24 | 8F` (0x2467/10 = 931,9 km, l'odomètre affiché).
 *
 * Le type 5 de cette machine est une trame de 6 octets (`05 31 32 33 34 9D`, « 1234 ») absente du
 * parseur officiel : elle est ignorée, comme le fait l'application d'origine.
 */
object EtwowProtocol {
    private const val START = 0x55

    fun frame(payload: IntArray): ByteArray {
        val all = intArrayOf(START) + payload
        val sum = all.sum() and 0xFF
        return ByteArray(all.size + 1) { index ->
            if (index < all.size) all[index].toByte() else sum.toByte()
        }
    }

    fun lock(): ByteArray = frame(intArrayOf(0x05, 0x05, 0x01))
    fun unlock(): ByteArray = frame(intArrayOf(0x05, 0x05, 0x00))
    fun lightOn(): ByteArray = frame(intArrayOf(0x06, 0x05, 0x01))
    fun lightOff(): ByteArray = frame(intArrayOf(0x06, 0x05, 0x00))
    fun setMode(mode: Int): ByteArray = frame(intArrayOf(0x02, 0x05, mode and 0x0F))

    fun decode(notification: ByteArray): Telemetry? {
        if (notification.size < 3) return null
        val type = notification[0].toInt() and 0xFF
        // Octets 1 et 2 : valeur sur 16 bits, poids faible d'abord.
        val value = (notification[1].toInt() and 0xFF) or ((notification[2].toInt() and 0xFF) shl 8)
        return when (type) {
            1 -> Telemetry(type, speedKmh = value / 10.0)
            2 -> value.takeIf { it in 0..100 }?.let { Telemetry(type, batteryPercent = it) }
            3 -> Telemetry(type, status = notification[1].toInt() and 0xFF)
            4 -> Telemetry(type, odoKm = value / 10.0)
            else -> null
        }
    }
}

fun ByteArray.hex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

data class Telemetry(
    val type: Int,
    val speedKmh: Double? = null,
    val batteryPercent: Int? = null,
    val status: Int? = null,
    val odoKm: Double? = null,
) {
    // Bits du statut, d'après le parseur officiel : 0x80 zero start, 0x20 verrou, 0x10 phare,
    // les trois bits de poids faible portent le cran de limite.
    val lightOn: Boolean? get() = status?.let { (it and 0x10) != 0 }
    val locked: Boolean? get() = status?.let { (it and 0x20) != 0 }
    val zeroStart: Boolean? get() = status?.let { (it and 0x80) != 0 }
    val mode: Int? get() = status?.let { it and 0x07 }
}
