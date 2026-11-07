package com.proxilink.backend.event

import kotlin.math.pow

enum class ProximityLevel {
    IMMEDIATE,
    VERY_CLOSE,
    NEARBY,
    FAR,
    OUT_OF_RANGE
}

/**
 * dBm bands. Higher RSSI = closer. Values are configurable at runtime via system settings.
 */
data class ProximityThresholds(
    val immediate: Int = -50,
    val veryClose: Int = -65,
    val nearby: Int = -80,
    val far: Int = -95
) {
    fun validate() {
        require(immediate <= 0) { "immediate threshold must be <= 0 dBm" }
        require(immediate > veryClose) { "immediate must be greater than veryClose" }
        require(veryClose > nearby) { "veryClose must be greater than nearby" }
        require(nearby > far) { "nearby must be greater than far" }
        require(far >= -127) { "far threshold must be >= -127 dBm" }
    }
}

class ProximityClassifier(private val thresholds: ProximityThresholds = ProximityThresholds()) {
    fun classify(smoothedRssi: Double?): ProximityLevel = when {
        smoothedRssi == null -> ProximityLevel.OUT_OF_RANGE
        smoothedRssi >= thresholds.immediate -> ProximityLevel.IMMEDIATE
        smoothedRssi >= thresholds.veryClose -> ProximityLevel.VERY_CLOSE
        smoothedRssi >= thresholds.nearby -> ProximityLevel.NEARBY
        smoothedRssi >= thresholds.far -> ProximityLevel.FAR
        else -> ProximityLevel.OUT_OF_RANGE
    }

    /**
     * Log-distance path-loss estimate. Deliberately approximate: walls, bodies, antenna
     * orientation and 2.4 GHz interference invalidate the free-space assumption.
     */
    fun estimateDistanceMeters(
        smoothedRssi: Double,
        txPowerAtOneMeter: Int = -59,
        pathLossExponent: Double = 2.0
    ): Double {
        require(pathLossExponent > 0) { "path loss exponent must be positive" }
        return 10.0.pow((txPowerAtOneMeter - smoothedRssi) / (10.0 * pathLossExponent))
    }
}
