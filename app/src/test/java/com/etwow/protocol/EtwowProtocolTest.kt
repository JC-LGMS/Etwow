package com.etwow.protocol

import org.junit.Assert.assertEquals
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

        // Type 5 (6 octets) absent du parseur officiel : ignoré.
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
        assertNull(EtwowProtocol.decode(bytes(2, 200, 0)))
        assertNull(EtwowProtocol.decode(bytes(2, 0xC8, 0x00)))
    }

    @Test
    fun `statut verrou phare et mode`() {
        // 0x73 = zero start non, verrou oui, phare oui, cran 3.
        val telemetry = EtwowProtocol.decode(bytes(3, 0x73, 0x00))
        assertEquals(true, telemetry?.locked)
        assertEquals(true, telemetry?.lightOn)
        assertEquals(false, telemetry?.zeroStart)
        assertEquals(SpeedModes.L3_25, telemetry?.mode)

        val lockedOnly = EtwowProtocol.decode(bytes(3, 0x22, 0x00))
        assertEquals(true, lockedOnly?.locked)
        assertEquals(false, lockedOnly?.lightOn)
        assertEquals(SpeedModes.L2_20, lockedOnly?.mode)

        assertEquals(true, EtwowProtocol.decode(bytes(3, 0x80, 0x00))?.zeroStart)
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
}
