package com.etwow.protocol

/**
 * Codec du protocole ETWOW (GT SE / SL / GTS).
 *
 * Trame : 55 <cmd> 05 <arg> <somme>. La somme est celle de tous les octets, 0x55 inclus,
 * tronquée à 8 bits. Les formules trip/odomètre sont l'hypothèse grand-boutiste : à confirmer
 * sur la trottinette réelle (le projet public additionnait les octets sans décalage).
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

    fun decode(bytes: ByteArray): Telemetry? {
        if (bytes.isEmpty()) return null
        val type = bytes[0].toInt() and 0xFF
        return when (type) {
            1 -> {
                if (bytes.size < 3) return null
                val low = bytes[1].toInt() and 0xFF
                val highFlag = bytes[2].toInt() and 0xFF
                Telemetry(type, speedKmh = low + if (highFlag == 1) 255 else 0)
            }

            2 -> {
                if (bytes.size < 2) return null
                val percent = bytes[1].toInt() and 0xFF
                if (percent !in 0..100) null else Telemetry(type, batteryPercent = percent)
            }

            3 -> {
                if (bytes.size < 2) return null
                Telemetry(type, status = bytes[1].toInt() and 0xFF)
            }

            4 -> {
                if (bytes.size < 3) return null
                val value = ((bytes[1].toInt() and 0xFF) shl 8) or (bytes[2].toInt() and 0xFF)
                Telemetry(type, tripKm = value)
            }

            5 -> {
                if (bytes.size < 6) return null
                val value = ((bytes[3].toInt() and 0xFF) shl 16) or
                    ((bytes[4].toInt() and 0xFF) shl 8) or
                    (bytes[5].toInt() and 0xFF)
                Telemetry(type, odoKm = value)
            }

            else -> null
        }
    }
}

fun ByteArray.hex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

data class Telemetry(
    val type: Int,
    val speedKmh: Int? = null,
    val batteryPercent: Int? = null,
    val status: Int? = null,
    val tripKm: Int? = null,
    val odoKm: Int? = null,
) {
    val lightOn: Boolean? get() = status?.let { ((it shr 4) and 0x1) != 0 }
    val locked: Boolean? get() = status?.let { ((it shr 4) and 0x2) != 0 }
    val zeroStart: Boolean? get() = status?.let { ((it shr 4) and 0x8) != 0 }
    val mode: Int? get() = status?.let { it and 0x0F }
}
