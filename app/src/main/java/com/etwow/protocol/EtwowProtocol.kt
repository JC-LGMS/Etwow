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

    /** Préfixes des appareils « nouveau protocole » (application officielle, `newDevice`). */
    val NEW_PROTOCOL_NAMES = listOf("BR2262e-s", "TK", "ET-WOW")

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
    fun zeroStart(on: Boolean): ByteArray = frame(intArrayOf(0x03, 0x05, if (on) 1 else 0))
    fun resetTrip(): ByteArray = frame(intArrayOf(0x09, 0x05, 0x00))
    fun horn(): ByteArray = frame(intArrayOf(0x18, 0x05, 0x00))

    // --- nouveau protocole : AA 55 00 0D <opcode> <action> 00 ×12 <xor> 0D ---------------

    fun newFrame(opcode: Int, action: Int): ByteArray {
        val frame = ByteArray(20)
        frame[0] = 0xAA.toByte()
        frame[1] = 0x55
        frame[2] = 0x00
        frame[3] = 0x0D
        frame[4] = opcode.toByte()
        frame[5] = action.toByte()
        var xor = 0
        for (index in 0 until 18) xor = xor xor (frame[index].toInt() and 0xFF)
        frame[18] = xor.toByte()
        frame[19] = 0x0D
        return frame
    }

    fun lockNew(): ByteArray = newFrame(0x01, 1)
    fun unlockNew(): ByteArray = newFrame(0x01, 0)
    fun lightOnNew(): ByteArray = newFrame(0x03, 1)
    fun lightOffNew(): ByteArray = newFrame(0x03, 0)
    fun setModeNew(mode: Int): ByteArray = newFrame(0x08, mode and 0x03)
    fun zeroStartNew(on: Boolean): ByteArray = newFrame(0x0E, if (on) 1 else 0)
    fun resetTripNew(): ByteArray = newFrame(0x0F, 0)
    fun hornNew(): ByteArray = newFrame(0x12, 0)
    fun driveModeNew(mode: Int): ByteArray = newFrame(0x09, mode and 0x03)

    /** Vrai si la trame est une notification du nouveau format, avec une somme XOR valide. */
    fun isNewFrame(bytes: ByteArray): Boolean {
        if (bytes.size != 20) return false
        if ((bytes[0].toInt() and 0xFF) != 0xAA) return false
        if ((bytes[1].toInt() and 0xFF) != 0x55) return false
        if ((bytes[19].toInt() and 0xFF) != 0x0D) return false
        var xor = 0
        for (index in 0 until 18) xor = xor xor (bytes[index].toInt() and 0xFF)
        return xor == (bytes[18].toInt() and 0xFF)
    }

    fun decode(notification: ByteArray): Telemetry? =
        if (isNewFrame(notification)) decodeNew(notification) else decodeOld(notification)

    private fun decodeNew(frame: ByteArray): Telemetry? {
        fun u(index: Int) = frame[index].toInt() and 0xFF
        return when (u(4)) {
            // 0x0B : vitesse, batterie, verrou, trip. Phare et zero start ne sont pas transmis.
            0x0B -> {
                val locked = (u(17) and 0x01) != 0
                Telemetry(
                    speedKmh = u(13).toDouble(),
                    batteryPercent = u(14).takeIf { it in 0..100 },
                    status = if (locked) 0x20 else 0,
                    statusMask = 0x20,
                    tripKm = ((u(15) shl 8) or u(16)) / 10.0,
                )
            }

            // 0x0C : odomètre, phare, zero start, mode de conduite. Verrou non transmis.
            0x0C -> {
                val flags = u(11)
                val odo = (u(5) shl 24) or (u(6) shl 16) or (u(7) shl 8) or u(8)
                Telemetry(
                    status = (if ((flags and 0x04) != 0) 0x10 else 0) or
                        (if ((flags and 0x80) != 0) 0x80 else 0),
                    statusMask = 0x90,
                    driveMode = when {
                        u(10) and 0x08 != 0 -> 0
                        u(10) and 0x10 != 0 -> 1
                        u(10) and 0x20 != 0 -> 2
                        u(10) and 0x04 != 0 -> 3
                        else -> null
                    },
                    odoKm = odo / 10.0,
                )
            }

            else -> null
        }
    }

    private fun decodeOld(notification: ByteArray): Telemetry? {
        // Trames de 4 octets, dernier octet = somme des précédents. Sans ce contrôle, une trame
        // abîmée peut annoncer 0 km/h et faire croire à l'arrêt (le verrou dépend de la vitesse).
        if (notification.size != 4) return null
        val sum = (0 until 3).sumOf { notification[it].toInt() and 0xFF } and 0xFF
        if (sum != (notification[3].toInt() and 0xFF)) return null
        val type = notification[0].toInt() and 0xFF
        // Octets 1 et 2 : valeur sur 16 bits, poids faible d'abord.
        val value = (notification[1].toInt() and 0xFF) or ((notification[2].toInt() and 0xFF) shl 8)
        return when (type) {
            1 -> Telemetry(speedKmh = value / 10.0)
            2 -> value.takeIf { it in 0..100 }?.let { Telemetry(batteryPercent = it) }
            3 -> Telemetry(
                status = notification[1].toInt() and 0xFF,
                speedMode = (notification[1].toInt() and 0xFF) and 0x07,
            )
            4 -> Telemetry(odoKm = value / 10.0)
            else -> null
        }
    }
}

fun ByteArray.hex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

data class Telemetry(
    val speedKmh: Double? = null,
    val batteryPercent: Int? = null,
    val status: Int? = null,
    /** Bits réellement transmis : un bit absent reste inconnu (null), jamais « faux ». */
    val statusMask: Int = 0xFF,
    val odoKm: Double? = null,
    val tripKm: Double? = null,
    val driveMode: Int? = null,
    /** Cran de limite de vitesse, seulement si la trame le porte (ancien protocole). */
    val speedMode: Int? = null,
) {
    private fun bit(mask: Int): Boolean? =
        status?.let { if ((statusMask and mask) == 0) null else (it and mask) != 0 }

    // Bits du statut, d'après le parseur officiel : 0x80 zero start, 0x20 verrou, 0x10 phare,
    // les trois bits de poids faible portent le cran de limite.
    val lightOn: Boolean? get() = bit(0x10)
    val locked: Boolean? get() = bit(0x20)
    val zeroStart: Boolean? get() = bit(0x80)
    val mode: Int? get() = speedMode
}
