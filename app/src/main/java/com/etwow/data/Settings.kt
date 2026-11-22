package com.etwow.data

import android.content.Context

/** Réglages persistants (SharedPreferences). */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("etwow", Context.MODE_PRIVATE)

    var autoConnect: Boolean
        get() = prefs.getBoolean("autoConnect", true)
        set(value) = prefs.edit().putBoolean("autoConnect", value).apply()

    var autoLight: Boolean
        get() = prefs.getBoolean("autoLight", false)
        set(value) = prefs.edit().putBoolean("autoLight", value).apply()

    var simulator: Boolean
        get() = prefs.getBoolean("simulator", false)
        set(value) = prefs.edit().putBoolean("simulator", value).apply()

    /**
     * Trip cumulé, calculé comme l'application officielle : somme des écarts d'odomètre.
     * La trottinette ne transmet pas le trip.
     */
    var savedTripKm: Double
        get() = prefs.getFloat("savedTrip", 0f).toDouble()
        set(value) = prefs.edit().putFloat("savedTrip", value.toFloat()).apply()

    var latitude: Double?
        get() = prefs.getFloat("latitude", 0f).takeIf { prefs.contains("latitude") }?.toDouble()
        set(value) = prefs.edit().putFloat("latitude", value?.toFloat() ?: 0f).apply {
            if (value == null) remove("latitude")
        }.apply()

    var longitude: Double?
        get() = prefs.getFloat("longitude", 0f).takeIf { prefs.contains("longitude") }?.toDouble()
        set(value) = prefs.edit().putFloat("longitude", value?.toFloat() ?: 0f).apply {
            if (value == null) remove("longitude")
        }.apply()
}
