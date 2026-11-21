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

    @Test
    fun `notification de vitesse`() {
        val telemetry = EtwowProtocol.decode(bytes(1, 17, 0))
        assertEquals(17, telemetry?.speedKmh)
        assertEquals(17 + 255, EtwowProtocol.decode(bytes(1, 17, 1))?.speedKmh)
    }

    @Test
    fun `batterie hors bornes ignoree`() {
        assertEquals(64, EtwowProtocol.decode(bytes(2, 64))?.batteryPercent)
        assertNull(EtwowProtocol.decode(bytes(2, 200)))
    }

    @Test
    fun `statut verrou phare et mode`() {
        val telemetry = EtwowProtocol.decode(bytes(3, 0x73))
        assertEquals(true, telemetry?.locked)
        assertEquals(true, telemetry?.lightOn)
        assertEquals(false, telemetry?.zeroStart)
        assertEquals(SpeedModes.L3_25, telemetry?.mode)

        val lockedOnly = EtwowProtocol.decode(bytes(3, 0x62))
        assertEquals(true, lockedOnly?.locked)
        assertEquals(false, lockedOnly?.lightOn)
        assertEquals(SpeedModes.L2_20, lockedOnly?.mode)
    }

    @Test
    fun `trip et odometre grand-boutistes`() {
        assertEquals(300, EtwowProtocol.decode(bytes(4, 0x01, 0x2C))?.tripKm)
        assertEquals(70_000, EtwowProtocol.decode(bytes(5, 0, 0, 0x01, 0x11, 0x70))?.odoKm)
    }

    @Test
    fun `trame tronquee ou inconnue sans crash`() {
        assertNull(EtwowProtocol.decode(ByteArray(0)))
        assertNull(EtwowProtocol.decode(bytes(1)))
        assertNull(EtwowProtocol.decode(bytes(4, 0x01)))
        assertNull(EtwowProtocol.decode(bytes(5, 0, 0, 1)))
        assertNull(EtwowProtocol.decode(bytes(9, 0, 0, 0, 0, 0)))
    }

    @Test
    fun `libelles des limites`() {
        assertTrue(SpeedModes.label(SpeedModes.L0_42).contains("42"))
        assertEquals(25, SpeedModes.kmh(SpeedModes.L3_25))
        assertNull(SpeedModes.kmh(7))
    }
}
