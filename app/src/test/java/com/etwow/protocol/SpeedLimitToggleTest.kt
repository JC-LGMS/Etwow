package com.etwow.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedLimitToggleTest {

    @Test
    fun `passe a 25 et memorise la limite courante`() {
        val decision = SpeedLimitToggle.toggle(currentMode = SpeedModes.L0_42, previousMode = null)
        assertEquals(SpeedModes.L3_25, decision?.targetMode)
        assertEquals(SpeedModes.L0_42, decision?.newPrevious)
    }

    @Test
    fun `second appui restaure la limite memorisee`() {
        val decision = SpeedLimitToggle.toggle(currentMode = SpeedModes.L3_25, previousMode = SpeedModes.L1_6)
        assertEquals(SpeedModes.L1_6, decision?.targetMode)
        assertNull(decision?.newPrevious)
    }

    @Test
    fun `sans memoire le retour se fait a 20 kmh`() {
        val decision = SpeedLimitToggle.toggle(currentMode = SpeedModes.L3_25, previousMode = null)
        assertEquals(SpeedModes.L2_20, decision?.targetMode)
    }

    @Test
    fun `une memoire a 25 est ignoree`() {
        val decision = SpeedLimitToggle.toggle(currentMode = SpeedModes.L3_25, previousMode = SpeedModes.L3_25)
        assertEquals(SpeedModes.L2_20, decision?.targetMode)
    }

    @Test
    fun `mode inconnu ne declenche rien`() {
        assertNull(SpeedLimitToggle.toggle(currentMode = null, previousMode = SpeedModes.L2_20))
    }
}
