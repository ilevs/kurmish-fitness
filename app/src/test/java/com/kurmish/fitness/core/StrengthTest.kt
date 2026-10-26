package com.kurmish.fitness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StrengthTest {
    @Test
    fun estimateMax() {
        assertEquals(100.0, Strength.estimateMax(100.0, 1)!!, 0.01)
        assertEquals(116.67, Strength.estimateMax(100.0, 5)!!, 0.01)
        assertNull(Strength.estimateMax(60.0, 15))
    }

    @Test
    fun percentRoundsToPlates() {
        assertEquals(87.5, Strength.percentOf(117.0, 75), 0.0)
        assertEquals(20.0, Strength.roundToPlates(5.0), 0.0)
    }
}
