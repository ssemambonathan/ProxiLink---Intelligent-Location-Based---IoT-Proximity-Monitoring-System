package com.proxilink.android.domain.proximity

import org.junit.Assert.assertEquals
import org.junit.Test

class ProximityClassifierTest {

    private val classifier = ProximityClassifier()

    @Test
    fun `immediate boundary is inclusive`() {
        assertEquals(ProximityLevel.IMMEDIATE, classifier.classify(-50.0))
        assertEquals(ProximityLevel.IMMEDIATE, classifier.classify(-30.0))
    }

    @Test
    fun `just below immediate is very close`() {
        assertEquals(ProximityLevel.VERY_CLOSE, classifier.classify(-50.01))
    }

    @Test
    fun `very close boundary is inclusive`() {
        assertEquals(ProximityLevel.VERY_CLOSE, classifier.classify(-65.0))
        assertEquals(ProximityLevel.NEARBY, classifier.classify(-65.01))
    }

    @Test
    fun `nearby boundary is inclusive`() {
        assertEquals(ProximityLevel.NEARBY, classifier.classify(-80.0))
        assertEquals(ProximityLevel.FAR, classifier.classify(-80.01))
    }

    @Test
    fun `far boundary is inclusive`() {
        assertEquals(ProximityLevel.FAR, classifier.classify(-95.0))
        assertEquals(ProximityLevel.OUT_OF_RANGE, classifier.classify(-95.01))
    }

    @Test
    fun `null and NaN classify as out of range`() {
        assertEquals(ProximityLevel.OUT_OF_RANGE, classifier.classify(null as Double?))
        assertEquals(ProximityLevel.OUT_OF_RANGE, classifier.classify(Double.NaN))
        assertEquals(ProximityLevel.OUT_OF_RANGE, classifier.classify(null as Int?))
    }

    @Test
    fun `integer samples use the same bands`() {
        assertEquals(ProximityLevel.IMMEDIATE, classifier.classify(-49))
        assertEquals(ProximityLevel.VERY_CLOSE, classifier.classify(-64))
        assertEquals(ProximityLevel.NEARBY, classifier.classify(-79))
        assertEquals(ProximityLevel.FAR, classifier.classify(-94))
        assertEquals(ProximityLevel.OUT_OF_RANGE, classifier.classify(-96))
    }

    @Test
    fun `custom thresholds move the boundaries`() {
        val custom = ProximityClassifier(ProximityThresholds(immediate = -40, veryClose = -55, nearby = -70, far = -85))

        assertEquals(ProximityLevel.IMMEDIATE, custom.classify(-40.0))
        assertEquals(ProximityLevel.VERY_CLOSE, custom.classify(-41.0))
        assertEquals(ProximityLevel.NEARBY, custom.classify(-56.0))
        assertEquals(ProximityLevel.FAR, custom.classify(-71.0))
        assertEquals(ProximityLevel.OUT_OF_RANGE, custom.classify(-86.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `thresholds must be strictly ordered`() {
        ProximityThresholds(immediate = -70, veryClose = -60, nearby = -80, far = -95)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `thresholds must remain negative`() {
        ProximityThresholds(immediate = 0)
    }
}
