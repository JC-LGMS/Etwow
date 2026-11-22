package com.etwow.protocol

/**
 * Codec du protocole ETWOW (GT SE / SL / GTS).
 *
 * Commande : `55 <cmd> 05 <arg> <somme>`, somme de tous les octets, 0x55 inclus.
 * Notification : `<type> <données…> <somme>`, somme des octets précédents. Vérifié sur une GTS
 * réelle le 22/11/2026 : `01 00 00 | 01`, `02 64 00 | 66`, `03 10 00 | 13`, `04 67 24 | 8F`.
 * Le type 5 de cette machine ne respecte pas la règle (`05 31 32 33 34 | 9D`) : la somme n'est
 * retirée que si elle tombe juste, sinon tous les octets restent des données.
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
        if (notification.size < 2) return null
        val type = notification.u(0)
        val body = if (checksumOk(notification)) {
            notification.copyOfRange(1, notification.size - 1)
        } else {
            notification.copyOfRange(1, notification.size)
        }
        return when (type) {
            1 -> {
                if (body.size < 2) null
                else Telemetry(type, speedKmh = body.u(0) + if (body.u(1) == 1) 255 else 0)
            }

            2 -> {
                if (body.isEmpty()) null
                else body.u(0).takeIf { it in 0..100 }?.let { Telemetry(type, batteryPercent = it) }
            }

            3 -> if (body.isEmpty()) null else Telemetry(type, status = body.u(0))

            4 -> {
                // Trip en mètres sur 16 bits : 0x6724 = 26404 m = 26,4 km.
                if (body.size < 2) null
                else Telemetry(type, tripKm = ((body.u(0) shl 8) or body.u(1)) / 1000.0)
            }

            5 -> {
                // Odomètre en chiffres décimaux : 31 32 33 34 = « 1234 » km.
                if (body.size < 4) null
                else {
                    val km = digits(body) ?: ((body.u(body.size - 3) shl 16) or
                        (body.u(body.size - 2) shl 8) or body.u(body.size - 1))
                    Telemetry(type, odoKm = km)
                }
            }

            else -> null
        }
    }

    private fun ByteArray.u(index: Int): Int = this[index].toInt() and 0xFF

    private fun checksumOk(frame: ByteArray): Boolean {
        val sum = (0 until frame.size - 1).sumOf { frame.u(it) } and 0xFF
        return frame.u(frame.size - 1) == sum
    }

    /**
     * Odomètre envoyé en chiffres décimaux (0x30..0x39). Tout le corps doit en être composé,
     * ou tout sauf son dernier octet quand la somme de contrôle annoncée est incohérente.
     */
    private fun digits(body: ByteArray): Int? {
        val text = body.takeWhile { (it.toInt() and 0xFF) in 0x30..0x39 }
        if (text.size < 2 || text.size < body.size - 1) return null
        var value = 0
        for (b in text) value = value * 10 + ((b.toInt() and 0xFF) - 0x30)
        return value
    }
}

fun ByteArray.hex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

data class Telemetry(
    val type: Int,
    val speedKmh: Int? = null,
    val batteryPercent: Int? = null,
    val status: Int? = null,
    val tripKm: Double? = null,
    val odoKm: Int? = null,
) {
    val lightOn: Boolean? get() = status?.let { ((it shr 4) and 0x1) != 0 }
    val locked: Boolean? get() = status?.let { ((it shr 4) and 0x2) != 0 }
    val zeroStart: Boolean? get() = status?.let { ((it shr 4) and 0x8) != 0 }
    val mode: Int? get() = status?.let { it and 0x0F }
}
