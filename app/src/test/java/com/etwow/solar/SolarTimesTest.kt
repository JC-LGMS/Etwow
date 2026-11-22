package com.etwow.solar

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SolarTimesTest {

    private val paris = ZoneId.of("Europe/Paris")

    private fun minutesOf(time: ZonedDateTime): Int = time.hour * 60 + time.minute

    private fun assertClose(expectedHour: Int, expectedMinute: Int, actual: ZonedDateTime?, label: String) {
        val expected = expectedHour * 60 + expectedMinute
        val delta = Math.abs(minutesOf(actual!!) - expected)
        assertTrue("$label : $actual au lieu de $expectedHour:$expectedMinute", delta <= 6)
    }

    @Test
    fun `paris solstice d ete`() {
        val day = SolarTimes.forDate(LocalDate.of(2026, 6, 21), 48.8566, 2.3522, paris)
        assertClose(5, 46, day.sunrise, "lever")
        assertClose(21, 57, day.sunset, "coucher")
    }

    @Test
    fun `paris solstice d hiver`() {
        val day = SolarTimes.forDate(LocalDate.of(2026, 12, 21), 48.8566, 2.3522, paris)
        assertClose(8, 41, day.sunrise, "lever")
        assertClose(16, 56, day.sunset, "coucher")
    }

    @Test
    fun `cas polaires`() {
        val ete = SolarTimes.forDate(LocalDate.of(2026, 6, 21), 69.65, 18.96, paris)
        assertTrue(ete.polarDay)
        assertNull(ete.sunrise)
        assertFalse(AutoLight.shouldBeOn(ZonedDateTime.of(2026, 6, 21, 23, 0, 0, 0, paris), ete))

        val hiver = SolarTimes.forDate(LocalDate.of(2026, 12, 21), 69.65, 18.96, paris)
        assertTrue(hiver.polarNight)
        assertTrue(AutoLight.shouldBeOn(ZonedDateTime.of(2026, 12, 21, 12, 0, 0, 0, paris), hiver))
    }

    @Test
    fun `phare allume la nuit et eteint le jour`() {
        val day = SolarTimes.forDate(LocalDate.of(2026, 6, 21), 48.8566, 2.3522, paris)
        assertTrue(AutoLight.shouldBeOn(ZonedDateTime.of(2026, 6, 21, 23, 0, 0, 0, paris), day))
        assertTrue(AutoLight.shouldBeOn(ZonedDateTime.of(2026, 6, 21, 3, 0, 0, 0, paris), day))
        assertFalse(AutoLight.shouldBeOn(ZonedDateTime.of(2026, 6, 21, 12, 0, 0, 0, paris), day))
    }

    @Test
    fun `marge de trente minutes autour des transitions`() {
        val day = SolarTimes.forDate(LocalDate.of(2026, 6, 21), 48.8566, 2.3522, paris)
        // Coucher 21:57 : allumé dès 21:27, encore éteint à 21:25.
        assertTrue(AutoLight.shouldBeOn(ZonedDateTime.of(2026, 6, 21, 21, 28, 0, 0, paris), day))
        assertFalse(AutoLight.shouldBeOn(ZonedDateTime.of(2026, 6, 21, 21, 25, 0, 0, paris), day))
        // Lever 05:46 : éteint dès 06:16, encore allumé à 06:14.
        assertFalse(AutoLight.shouldBeOn(ZonedDateTime.of(2026, 6, 21, 6, 17, 0, 0, paris), day))
        assertTrue(AutoLight.shouldBeOn(ZonedDateTime.of(2026, 6, 21, 6, 14, 0, 0, paris), day))
    }

    @Test
    fun `prochaine transition`() {
        val day = SolarTimes.forDate(LocalDate.of(2026, 6, 21), 48.8566, 2.3522, paris)
        val noon = ZonedDateTime.of(2026, 6, 21, 12, 0, 0, 0, paris)
        val next = AutoLight.nextTransition(noon, day)!!
        assertEquals(21, next.hour)
        assertEquals(27, next.minute)
    }
}
