package com.proxilink.backend.event

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.abs

class ProximityClassifierTest {

    private val classifier = ProximityClassifier()

    @Test
    fun `classifies exact band boundaries`() {
        assertEquals(ProximityLevel.IMMEDIATE, classifier.classify(-49.0))
        assertEquals(ProximityLevel.IMMEDIATE, classifier.classify(-50.0))
        assertEquals(ProximityLevel.VERY_CLOSE, classifier.classify(-50.1))
        assertEquals(ProximityLevel.VERY_CLOSE, classifier.classify(-65.0))
        assertEquals(ProximityLevel.NEARBY, classifier.classify(-65.1))
        assertEquals(ProximityLevel.NEARBY, classifier.classify(-80.0))
        assertEquals(ProximityLevel.FAR, classifier.classify(-80.1))
        assertEquals(ProximityLevel.FAR, classifier.classify(-95.0))
        assertEquals(ProximityLevel.OUT_OF_RANGE, classifier.classify(-95.1))
    }

    @Test
    fun `treats missing signal as out of range`() {
        assertEquals(ProximityLevel.OUT_OF_RANGE, classifier.classify(null))
    }

    @Test
    fun `honours custom thresholds`() {
        val custom = ProximityClassifier(
            ProximityThresholds(immediate = -40, veryClose = -55, nearby = -70, far = -85)
        )
        assertEquals(ProximityLevel.IMMEDIATE, custom.classify(-40.0))
        assertEquals(ProximityLevel.VERY_CLOSE, custom.classify(-54.9))
        assertEquals(ProximityLevel.NEARBY, custom.classify(-69.9))
        assertEquals(ProximityLevel.FAR, custom.classify(-84.9))
        assertEquals(ProximityLevel.FAR, custom.classify(-85.0))
        assertEquals(ProximityLevel.OUT_OF_RANGE, custom.classify(-85.1))
    }

    @Test
    fun `rejects unordered thresholds`() {
        assertThrows<IllegalArgumentException> {
            ProximityThresholds(immediate = -70, veryClose = -60, nearby = -80, far = -95).validate()
        }
        assertThrows<IllegalArgumentException> {
            ProximityThresholds(immediate = 5, veryClose = -65, nearby = -80, far = -95).validate()
        }
        assertThrows<IllegalArgumentException> {
            ProximityThresholds(immediate = -50, veryClose = -65, nearby = -80, far = -130).validate()
        }
    }

    @Test
    fun `distance estimate is one metre at calibrated tx power`() {
        val distance = classifier.estimateDistanceMeters(-59.0)
        assertTrue(abs(distance - 1.0) < 0.001, "expected ~1 m, got $distance")
    }

    @Test
    fun `distance grows monotonically as signal weakens`() {
        val samples = listOf(-40.0, -50.0, -60.0, -70.0, -80.0, -90.0)
        val distances = samples.map { classifier.estimateDistanceMeters(it) }
        assertEquals(distances.sorted(), distances)
        assertTrue(distances.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun `distance estimate rejects invalid path loss exponent`() {
        assertThrows<IllegalArgumentException> {
            classifier.estimateDistanceMeters(-60.0, pathLossExponent = 0.0)
        }
    }
}
