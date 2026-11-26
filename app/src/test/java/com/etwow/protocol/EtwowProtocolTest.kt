package com.etwow.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EtwowProtocolTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `trames de commande exactes`() {
        assertEquals("55 05 05 01 60", EtwowProtocol.lock().hex())
        assertEquals("55 05 05 00 5F", EtwowProtocol.unlock().hex())
        assertEquals("55 06 05 01 61", EtwowProtocol.lightOn().hex())
        assertEquals("55 06 05 00 60", EtwowProtocol.lightOff().hex())
        assertEquals("55 02 05 00 5C", EtwowProtocol.setMode(SpeedModes.L0_42).hex())
        assertEquals("55 02 05 01 5D", EtwowProtocol.setMode(SpeedModes.L1_6).hex())
        assertEquals("55 02 05 02 5E", EtwowProtocol.setMode(SpeedModes.L2_20).hex())
        assertEquals("55 02 05 03 5F", EtwowProtocol.setMode(SpeedModes.L3_25).hex())
    }

    @Test
    fun `somme de controle tronquee a 8 bits`() {
        // 0x55 + 0xFF + 0x05 + 0xFF = 0x258 -> 0x58
        assertEquals("55 FF 05 FF 58", EtwowProtocol.frame(intArrayOf(0xFF, 0x05, 0xFF)).hex())
    }

    /** Trames relevées sur une GTS réelle le 22/11/2026, comparées à son afficheur. */
    @Test
    fun `trames reelles de la GTS`() {
        assertEquals(0.0, EtwowProtocol.decode(bytes(1, 0x00, 0x00, 0x01))!!.speedKmh!!, 0.0001)
        assertEquals(100, EtwowProtocol.decode(bytes(2, 0x64, 0x00, 0x66))!!.batteryPercent)

        val status = EtwowProtocol.decode(bytes(3, 0x10, 0x00, 0x13))!!
        assertEquals(true, status.lightOn)
        assertEquals(false, status.locked)
        assertEquals(SpeedModes.L0_42, status.mode)

        // 0x2467 / 10 = 931,9 km : l'odomètre affiché par la trottinette (931).
        assertEquals(931.9, EtwowProtocol.decode(bytes(4, 0x67, 0x24, 0x8F))!!.odoKm!!, 0.0001)

        // Type 5 (6 octets) absent du parseur officiel : ignoré (et sa somme ne suit pas la règle).
        assertNull(EtwowProtocol.decode(bytes(5, 0x31, 0x32, 0x33, 0x34, 0x9D)))
    }

    @Test
    fun `valeurs petit-boutistes`() {
        // 0x00FD = 253 -> 25,3 km/h
        assertEquals(25.3, EtwowProtocol.decode(bytes(1, 0xFD, 0x00, 0xFE))!!.speedKmh!!, 0.0001)
        // 0x0064 = 100 %
        assertEquals(100, EtwowProtocol.decode(bytes(2, 0x64, 0x00, 0x66))!!.batteryPercent)
    }

    @Test
    fun `batterie hors bornes ignoree`() {
        assertNull(EtwowProtocol.decode(bytes(2, 0xC8, 0x00, 0xCA)))
        assertNull(EtwowProtocol.decode(bytes(2, 0x64, 0x00)))
    }

    @Test
    fun `somme de controle fausse rejetee`() {
        // Sans ce contrôle, une trame abîmée peut annoncer 0 km/h et faire croire à l'arrêt.
        assertNull(EtwowProtocol.decode(bytes(1, 0x00, 0x00, 0x02)))
        assertNull(EtwowProtocol.decode(bytes(3, 0x10, 0x00, 0x00)))
    }

    @Test
    fun `statut verrou phare et mode`() {
        // 0x73 = zero start non, verrou oui, phare oui, cran 3.
        val telemetry = EtwowProtocol.decode(bytes(3, 0x73, 0x00, 0x76))
        assertEquals(true, telemetry?.locked)
        assertEquals(true, telemetry?.lightOn)
        assertEquals(false, telemetry?.zeroStart)
        assertEquals(SpeedModes.L3_25, telemetry?.mode)

        val lockedOnly = EtwowProtocol.decode(bytes(3, 0x22, 0x00, 0x25))
        assertEquals(true, lockedOnly?.locked)
        assertEquals(false, lockedOnly?.lightOn)
        assertEquals(SpeedModes.L2_20, lockedOnly?.mode)

        assertEquals(true, EtwowProtocol.decode(bytes(3, 0x80, 0x00, 0x83))?.zeroStart)
    }

    @Test
    fun `trame tronquee ou inconnue sans crash`() {
        assertNull(EtwowProtocol.decode(ByteArray(0)))
        assertNull(EtwowProtocol.decode(bytes(1)))
        assertNull(EtwowProtocol.decode(bytes(4, 0x01)))
        assertNull(EtwowProtocol.decode(bytes(9, 0, 0, 0)))
    }

    @Test
    fun `libelles des limites`() {
        assertTrue(SpeedModes.label(SpeedModes.L0_42).contains("42"))
        assertEquals(25, SpeedModes.kmh(SpeedModes.L3_25))
        assertNull(SpeedModes.kmh(7))
    }

    // --- nouveau protocole (trames de 20 octets) -------------------------------------

    private fun newNotify(opcode: Int, fill: (IntArray) -> Unit): ByteArray {
        val b = IntArray(20)
        b[0] = 0xAA
        b[1] = 0x55
        b[2] = 0x00
        b[3] = 0x0D
        b[4] = opcode
        fill(b)
        var xor = 0
        for (index in 0 until 18) xor = xor xor b[index]
        b[18] = xor
        b[19] = 0x0D
        return ByteArray(20) { b[it].toByte() }
    }

    @Test
    fun `commandes du nouveau protocole`() {
        assertEquals(
            "AA 55 00 0D 01 01 00 00 00 00 00 00 00 00 00 00 00 00 F2 0D",
            EtwowProtocol.lockNew().hex(),
        )
        assertEquals(0xF0, EtwowProtocol.lightOnNew()[18].toInt() and 0xFF)
        assertEquals(0xF9, EtwowProtocol.setModeNew(SpeedModes.L3_25)[18].toInt() and 0xFF)
        assertEquals(0x08, EtwowProtocol.setModeNew(SpeedModes.L3_25)[4].toInt() and 0xFF)
        assertEquals(0x03, EtwowProtocol.setModeNew(SpeedModes.L3_25)[5].toInt() and 0xFF)
    }

    @Test
    fun `notification 0B vitesse batterie verrou trip`() {
        val frame = newNotify(0x0B) {
            it[13] = 25
            it[14] = 80
            it[15] = 0x04
            it[16] = 0xD2
            it[17] = 0x01
        }
        assertTrue(EtwowProtocol.isNewFrame(frame))
        val telemetry = EtwowProtocol.decode(frame)!!
        assertEquals(25.0, telemetry.speedKmh!!, 0.0001)
        assertEquals(80, telemetry.batteryPercent)
        assertEquals(123.4, telemetry.tripKm!!, 0.0001)
        assertEquals(true, telemetry.locked)
        // Phare et zero start ne sont pas dans cette trame : inconnus, jamais « faux ».
        assertNull(telemetry.lightOn)
        assertNull(telemetry.zeroStart)
    }

    @Test
    fun `notification 0C odometre phare et mode`() {
        val frame = newNotify(0x0C) {
            it[7] = 0x24
            it[8] = 0x67
            it[10] = 0x20
            it[11] = 0x04
        }
        val telemetry = EtwowProtocol.decode(frame)!!
        assertEquals(931.9, telemetry.odoKm!!, 0.0001)
        assertEquals(true, telemetry.lightOn)
        assertEquals(false, telemetry.zeroStart)
        // Le verrou n'est pas dans cette trame : inconnu, jamais « faux ».
        assertNull(telemetry.locked)
        // 0x20 = DRIVE ; ce protocole ne transmet pas la limite de vitesse.
        assertEquals(2, telemetry.driveMode)
        assertNull(telemetry.mode)
    }

    @Test
    fun `nouvelle trame sans octet de fin ignoree`() {
        val frame = newNotify(0x0B) { it[13] = 25 }
        frame[19] = 0x0E
        assertFalse(EtwowProtocol.isNewFrame(frame))
        assertNull(EtwowProtocol.decode(frame))
    }

    @Test
    fun `nouvelle trame avec xor faux ignoree`() {
        val frame = newNotify(0x0B) { it[13] = 25 }
        frame[18] = (frame[18] + 1).toByte()
        assertNull(EtwowProtocol.decode(frame))

        // Une trame du protocole historique reste reconnue comme telle.
        val old = bytes(1, 0, 0, 1)
        assertFalse(EtwowProtocol.isNewFrame(old))
        assertEquals(0.0, EtwowProtocol.decode(old)!!.speedKmh!!, 0.0001)
    }
}
