package com.proxilink.android.domain.proximity

import kotlin.math.pow

/**
 * Log-distance path loss model: d = 10 ^ ((txPower - rssi) / (10 * n)).
 *
 * The result is an approximation only. Walls, human bodies, antenna orientation and 2.4 GHz
 * interference routinely invalidate it, which is why every screen labels the value "estimate".
 */
class DistanceEstimator(
    private val txPowerAtOneMeter: Double = DEFAULT_TX_POWER,
    private val pathLossExponent: Double = DEFAULT_PATH_LOSS_EXPONENT,
) {
    init {
        require(pathLossExponent > 0.0) { "path loss exponent must be positive" }
        require(txPowerAtOneMeter <= 0.0) { "tx power must be <= 0 dBm" }
    }

    /** Returns metres, clamped to the physically plausible range for a phone BLE radio. */
    fun estimateMeters(rssi: Double): Double {
        val exponent = (txPowerAtOneMeter - rssi) / (10.0 * pathLossExponent)
        return 10.0.pow(exponent).coerceIn(MIN_METERS, MAX_METERS)
    }

    fun estimateMeters(rssi: Int): Double = estimateMeters(rssi.toDouble())

    companion object {
        const val DEFAULT_TX_POWER = -59.0
        const val DEFAULT_PATH_LOSS_EXPONENT = 2.0
        /**
         * Numeric floor for the estimate. Kept low enough that the clamp never collapses two
         * distinct strong-signal readings to the same distance (which would break monotonicity).
         */
        const val MIN_METERS = 0.01
        const val MAX_METERS = 200.0

        fun format(metres: Double?): String {
            if (metres == null || metres.isNaN()) return "unknown"
            return if (metres < 1.0) {
                "<1 m (estimate)"
            } else {
                "~${String.format(java.util.Locale.US, "%.1f", metres)} m (estimate)"
            }
        }
    }
}
