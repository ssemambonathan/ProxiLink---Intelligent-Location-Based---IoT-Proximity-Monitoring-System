package com.proxilink.android.domain.proximity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RssiSmootherTest {

    @Test
    fun `first sample primes median and ema with the raw value`() {
        val smoother = RssiSmoother()

        assertEquals(-64.0, smoother.accept(-64), 0.0001)
        assertTrue(smoother.isPrimed)
        assertEquals(1, smoother.sampleCount)
    }

    @Test
    fun `constant input stays constant`() {
        val smoother = RssiSmoother()
        repeat(10) { assertEquals(-70.0, smoother.accept(-70), 0.0001) }
    }

    @Test
    fun `median rejects a single spike`() {
        val smoother = RssiSmoother()
        listOf(-60, -61, -60, -59).forEach { smoother.accept(it) }
        val before = smoother.current()!!

        val after = smoother.accept(-20.0.toInt())

        // Window is [-61, -60, -60, -59, -20]; median is -60, so the spike barely moves the EMA.
        val expected = 0.3 * -60 + 0.7 * before
        assertEquals(expected, after, 0.0001)
        assertTrue("spike should not jump the smoothed value", after < before + 3.0)
    }

    @Test
    fun `window keeps only the configured number of samples`() {
        val smoother = RssiSmoother(windowSize = 5)
        repeat(20) { smoother.accept(-50 - it) }

        assertEquals(5, smoother.sampleCount)
    }

    @Test
    fun `ema follows a sustained level change gradually`() {
        val smoother = RssiSmoother(alpha = 0.3)
        repeat(5) { smoother.accept(-90) }
        val before = smoother.current()!!

        var value = before
        repeat(4) { value = smoother.accept(-50) }

        assertTrue("value should rise towards -50", value > before)
        assertTrue("value should not overshoot", value < -50.0)
    }

    @Test
    fun `reset clears history and requires a new primer`() {
        val smoother = RssiSmoother()
        smoother.accept(-80)
        smoother.reset()

        assertNull(smoother.current())
        assertFalse(smoother.isPrimed)
        assertEquals(0, smoother.sampleCount)
        assertEquals(-40.0, smoother.accept(-40), 0.0001)
    }

    @Test
    fun `even window uses the average of the two middle samples`() {
        val smoother = RssiSmoother(windowSize = 4, alpha = 1.0)
        listOf(-70, -60, -50, -40).forEach { smoother.accept(it) }

        // Sorted: -70, -60, -50, -40 -> median (-60 + -50) / 2 = -55; alpha 1.0 uses it directly.
        assertEquals(-55.0, smoother.current()!!, 0.0001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `accepts only negative dBm values`() {
        RssiSmoother().accept(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects values below -127`() {
        RssiSmoother().accept(-200)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects an invalid window`() {
        RssiSmoother(windowSize = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects an alpha outside 0 to 1`() {
        RssiSmoother(alpha = 1.5)
    }
}
