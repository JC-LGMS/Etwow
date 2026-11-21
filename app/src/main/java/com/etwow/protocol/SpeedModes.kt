package com.etwow.protocol

object SpeedModes {
    const val L0_42 = 0
    const val L1_6 = 1
    const val L2_20 = 2
    const val L3_25 = 3

    val all = listOf(L0_42, L1_6, L2_20, L3_25)

    fun kmh(mode: Int): Int? = when (mode) {
        L0_42 -> 42
        L1_6 -> 6
        L2_20 -> 20
        L3_25 -> 25
        else -> null
    }

    fun label(mode: Int): String = when (mode) {
        L0_42 -> "42 km/h (privé)"
        L1_6 -> "6 km/h"
        L2_20 -> "20 km/h"
        L3_25 -> "25 km/h"
        else -> "mode $mode"
    }
}

/** Bascule du widget : 25 km/h <-> limite précédente. */
object SpeedLimitToggle {
    const val MODE_25 = SpeedModes.L3_25
    const val FALLBACK = SpeedModes.L2_20

    data class Decision(val targetMode: Int, val newPrevious: Int?)

    fun toggle(currentMode: Int?, previousMode: Int?): Decision? {
        if (currentMode == null) return null
        return if (currentMode == MODE_25) {
            val restore = previousMode?.takeIf { it != MODE_25 } ?: FALLBACK
            Decision(restore, null)
        } else {
            Decision(MODE_25, currentMode)
        }
    }
}
