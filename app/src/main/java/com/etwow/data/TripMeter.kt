package com.etwow.data

/**
 * Trip cumulé, comme l'application officielle : la trottinette ne transmet pas le trip, elle
 * transmet l'odomètre. On ajoute chaque écart positif ; un odomètre qui recule signale une remise
 * à zéro du compteur et ne retire rien.
 */
object TripMeter {
    fun next(savedTripKm: Double, previousOdoKm: Double?, odoKm: Double): Double =
        if (previousOdoKm != null && odoKm > previousOdoKm) {
            savedTripKm + (odoKm - previousOdoKm)
        } else {
            savedTripKm
        }
}
