package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max

class ExampleUnitTest {

    @Test
    fun timeFormatting_computesDurationAccurately() {
        val arrivalUtc = 1700000000000L
        val departureUtc = arrivalUtc + (3750 * 1000L) // 1 hr, 2 mins, 30 secs
        val durationSeconds = (departureUtc - arrivalUtc) / 1000L

        assertEquals(3750L, durationSeconds)
        val minutes = durationSeconds / 60
        val hours = minutes / 60
        val remMin = minutes % 60

        assertEquals(1L, hours)
        assertEquals(2L, remMin)
    }

    @Test
    fun confidenceScore_staysWithinUnitRange() {
        val pointCount = 45
        val baseConfidence = 0.7f
        val computed = (baseConfidence + (pointCount * 0.03f)).coerceAtMost(1.0f)

        assertTrue(computed <= 1.0f)
        assertTrue(computed >= 0.7f)
    }
}
