package com.kurmish.fitness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun warmUpStartsWithEmptyBarAndRespectsRepCap() {
        val sets = Strength.warmUp(150.0, maxReps = 6)
        assertEquals(SetGroup(20.0, 6), sets.first())
        assertTrue(sets.all { it.reps <= 6 })
        assertTrue(sets.last().weightKg!! < 150.0)
        assertEquals(sets.map { it.weightKg }.sortedBy { it }, sets.map { it.weightKg })
    }

    @Test
    fun enduranceWarmUpKeepsEightReps() {
        assertTrue(Strength.warmUp(100.0, maxReps = 10, minReps = 8).all { it.reps >= 8 })
    }
}
