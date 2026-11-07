package com.proxilink.android.domain.proximity

/**
 * Sliding-window median followed by an exponential moving average.
 *
 * Median of the last [windowSize] samples removes single-sample spikes (multipath, antenna
 * orientation); the EMA then smooths the remaining jitter. Pure Kotlin so it is unit-testable
 * without Android.
 */
class RssiSmoother(
    private val windowSize: Int = DEFAULT_WINDOW,
    private val alpha: Double = DEFAULT_ALPHA,
) {
    init {
        require(windowSize > 0) { "windowSize must be positive" }
        require(alpha > 0.0 && alpha <= 1.0) { "alpha must be in (0, 1]" }
    }

    private val window = ArrayDeque<Int>(windowSize)
    private var ema: Double? = null

    val sampleCount: Int get() = window.size

    val isPrimed: Boolean get() = ema != null

    /** Drops all history; the next accepted sample restarts the EMA. */
    fun reset() {
        window.clear()
        ema = null
    }

    /**
     * Feeds a raw RSSI sample and returns the smoothed value.
     * Valid BLE RSSI is negative dBm; 0 is reported by some stacks when unknown, so it is rejected.
     */
    fun accept(rssi: Int): Double {
        require(rssi in MIN_RSSI..MAX_RSSI) { "RSSI must be within $MIN_RSSI..$MAX_RSSI dBm, was $rssi" }
        window.addLast(rssi)
        if (window.size > windowSize) window.removeFirst()
        val median = medianOf(window)
        val previous = ema
        val next = if (previous == null) median else alpha * median + (1.0 - alpha) * previous
        ema = next
        return next
    }

    fun current(): Double? = ema

    private fun medianOf(values: Collection<Int>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle].toDouble()
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }

    companion object {
        const val DEFAULT_WINDOW = 5
        const val DEFAULT_ALPHA = 0.3
        const val MIN_RSSI = -127
        /** 0 dBm is reported by some stacks when the value is unknown, so it is not a valid reading. */
        const val MAX_RSSI = -1
    }
}
