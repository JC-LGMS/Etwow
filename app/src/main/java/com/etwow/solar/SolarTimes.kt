package com.etwow.solar

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/** Calcul local du lever et du coucher du soleil (algorithme NOAA simplifié). */
object SolarTimes {
    data class SolarDay(
        val sunrise: ZonedDateTime?,
        val sunset: ZonedDateTime?,
        val polarDay: Boolean = false,
        val polarNight: Boolean = false,
    )

    fun forDate(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId): SolarDay {
        val n = date.dayOfYear
        val gamma = 2.0 * PI / 365.0 * (n - 1)

        val eqTime = 229.18 * (
            0.000075 +
                0.001868 * cos(gamma) -
                0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) -
                0.040849 * sin(2 * gamma)
            )

        val declination = 0.006918 -
            0.399912 * cos(gamma) +
            0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) +
            0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) +
            0.00148 * sin(3 * gamma)

        val latRad = Math.toRadians(latitude)
        val cosHourAngle = cos(Math.toRadians(90.833)) / (cos(latRad) * cos(declination)) -
            tan(latRad) * tan(declination)

        if (cosHourAngle > 1.0) return SolarDay(null, null, polarNight = true)
        if (cosHourAngle < -1.0) return SolarDay(null, null, polarDay = true)

        val hourAngle = Math.toDegrees(acos(cosHourAngle))
        val sunriseUtcMinutes = 720.0 - 4.0 * (longitude + hourAngle) - eqTime
        val sunsetUtcMinutes = 720.0 - 4.0 * (longitude - hourAngle) - eqTime

        return SolarDay(
            sunrise = utcMinutes(date, sunriseUtcMinutes, zone),
            sunset = utcMinutes(date, sunsetUtcMinutes, zone),
        )
    }

    private fun utcMinutes(date: LocalDate, minutes: Double, zone: ZoneId): ZonedDateTime =
        date.atStartOfDay(ZoneOffset.UTC)
            .plusSeconds((minutes * 60).roundToLong())
            .withZoneSameInstant(zone)
}

object AutoLight {
    /** Phare allumé 30 min avant le coucher, éteint 30 min après le lever. */
    const val MARGIN_MINUTES = 30L

    fun shouldBeOn(
        now: ZonedDateTime,
        day: SolarTimes.SolarDay,
        marginMinutes: Long = MARGIN_MINUTES,
    ): Boolean {
        if (day.polarNight) return true
        if (day.polarDay) return false
        val sunrise = day.sunrise ?: return false
        val sunset = day.sunset ?: return false
        val onFrom = sunset.minusMinutes(marginMinutes)
        val onUntil = sunrise.plusMinutes(marginMinutes)
        return !now.isBefore(onFrom) || now.isBefore(onUntil)
    }

    fun nextTransition(
        now: ZonedDateTime,
        day: SolarTimes.SolarDay,
        marginMinutes: Long = MARGIN_MINUTES,
    ): ZonedDateTime? {
        val candidates = listOfNotNull(
            day.sunrise?.plusMinutes(marginMinutes),
            day.sunset?.minusMinutes(marginMinutes),
        )
        return candidates.filter { it.isAfter(now) }.minOrNull()
    }
}
