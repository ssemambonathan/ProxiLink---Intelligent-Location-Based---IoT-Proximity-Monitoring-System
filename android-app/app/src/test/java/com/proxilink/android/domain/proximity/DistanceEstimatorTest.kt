package com.proxilink.android.domain.proximity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class DistanceEstimatorTest {

    private val estimator = DistanceEstimator()

    @Test
    fun `tx power corresponds to one metre`() {
        assertEquals(1.0, estimator.estimateMeters(-59.0), 0.001)
        assertEquals(1.0, estimator.estimateMeters(-59), 0.001)
    }

    @Test
    fun `distance is strictly monotonic as signal weakens`() {
        var previous = estimator.estimateMeters(-30.0)
        for (rssi in -31 downTo -100) {
            val current = estimator.estimateMeters(rssi.toDouble())
            assertTrue("weaker RSSI must never produce a shorter distance at $rssi", current > previous)
            previous = current
        }
    }

    @Test
    fun `ten dB weaker is roughly three times further in free space`() {
        val near = estimator.estimateMeters(-59.0)
        val far = estimator.estimateMeters(-69.0)

        // n = 2 -> a 10 dB increase in path loss means 10 ^ (10 / 20) = 3.16x the distance.
        assertEquals(10.0.pow(10.0 / (10.0 * DistanceEstimator.DEFAULT_PATH_LOSS_EXPONENT)), far / near, 0.01)
    }

    @Test
    fun `stronger signal yields shorter distance than weaker signal`() {
        assertTrue(estimator.estimateMeters(-45) < estimator.estimateMeters(-85))
    }

    @Test
    fun `estimate is clamped to a plausible range`() {
        assertEquals(DistanceEstimator.MAX_METERS, estimator.estimateMeters(-127.0), 0.0001)
        assertEquals(DistanceEstimator.MIN_METERS, estimator.estimateMeters(-10.0), 0.0001)
    }

    @Test
    fun `format labels the value as an estimate`() {
        val formatted = DistanceEstimator.format(5.2)
        assertTrue(formatted.contains("estimate"))
        assertTrue(formatted.contains("5.2"))
        assertTrue(DistanceEstimator.format(null).contains("unknown"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `path loss exponent must be positive`() {
        DistanceEstimator(pathLossExponent = 0.0)
    }
}
