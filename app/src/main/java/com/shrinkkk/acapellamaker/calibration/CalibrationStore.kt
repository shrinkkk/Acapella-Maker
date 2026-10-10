package com.shrinkkk.acapellamaker.calibration

import android.content.Context

/** Measured round-trip delay per audio route, kept across app restarts. */
class CalibrationStore(context: Context) {
    private val prefs = context.getSharedPreferences("calibration", Context.MODE_PRIVATE)

    fun roundTripMs(route: AudioRoute): Double? {
        val key = "roundTripMs_${route.key}"
        return if (prefs.contains(key)) prefs.getFloat(key, 0f).toDouble() else null
    }

    fun save(route: AudioRoute, roundTripMs: Double) {
        prefs.edit().putFloat("roundTripMs_${route.key}", roundTripMs.toFloat()).apply()
    }

    fun clear(route: AudioRoute) {
        prefs.edit().remove("roundTripMs_${route.key}").apply()
    }
}
