package com.proxilink.android.domain.proximity

/**
 * Coarse proximity bands. RSSI is not a distance sensor, so the UI always presents a band plus an
 * explicitly labelled estimate.
 */
enum class ProximityLevel(val label: String, val description: String) {
    IMMEDIATE("Immediate", "Within arm's reach"),
    VERY_CLOSE("Very close", "A few metres away"),
    NEARBY("Nearby", "Same room or adjacent space"),
    FAR("Far", "Detectable but distant"),
    OUT_OF_RANGE("Out of range", "No recent advertisement"),
    ;

    val isDetected: Boolean get() = this != OUT_OF_RANGE

    companion object {
        fun fromWire(value: String?): ProximityLevel =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OUT_OF_RANGE
    }
}

/**
 * Thresholds are upper-inclusive lower bounds: a smoothed RSSI of exactly -50 is IMMEDIATE,
 * -50.4 is VERY_CLOSE. Ordering is validated so a bad server payload cannot invert the bands.
 */
data class ProximityThresholds(
    val immediate: Int = -50,
    val veryClose: Int = -65,
    val nearby: Int = -80,
    val far: Int = -95,
) {
    init {
        require(immediate < 0) { "immediate must be negative" }
        require(immediate > veryClose) { "immediate must be greater than veryClose" }
        require(veryClose > nearby) { "veryClose must be greater than nearby" }
        require(nearby > far) { "nearby must be greater than far" }
        require(far >= -127) { "far must be >= -127" }
    }

    companion object {
        val DEFAULT = ProximityThresholds()
    }
}

class ProximityClassifier(
    private val thresholds: ProximityThresholds = ProximityThresholds.DEFAULT,
) {
    fun classify(smoothedRssi: Double?): ProximityLevel {
        if (smoothedRssi == null || smoothedRssi.isNaN()) return ProximityLevel.OUT_OF_RANGE
        return when {
            smoothedRssi >= thresholds.immediate -> ProximityLevel.IMMEDIATE
            smoothedRssi >= thresholds.veryClose -> ProximityLevel.VERY_CLOSE
            smoothedRssi >= thresholds.nearby -> ProximityLevel.NEARBY
            smoothedRssi >= thresholds.far -> ProximityLevel.FAR
            else -> ProximityLevel.OUT_OF_RANGE
        }
    }

    fun classify(rssi: Int?): ProximityLevel = classify(rssi?.toDouble())

    fun withThresholds(thresholds: ProximityThresholds): ProximityClassifier =
        ProximityClassifier(thresholds)
}
