package com.kurmish.fitness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WarmUpTest {
    @Test
    fun squatUpTo100MatchesTable() {
        val sets = WarmUp.sets(Exercise.Squat, 100.0)
        val expected = listOf(
            WarmUpSet(20.0, 3, "5"),
            WarmUpSet(40.0, 1, "5"),
            WarmUpSet(55.0, 1, "3"),
            WarmUpSet(70.0, 1, "2"),
            WarmUpSet(80.0, 1, "1"),
            WarmUpSet(90.0, 1, "1"),
        )
        assertEquals(expected, sets)
    }

    @Test
    fun heavyDeadliftStartsAtThirtyPercent() {
        val sets = WarmUp.sets(Exercise.Deadlift, 200.0)
        assertEquals(WarmUpSet(60.0, 1, "8"), sets.first())
        assertEquals(180.0, sets.last().weightKg, 0.0)
    }

    @Test
    fun benchRoundsToPlatesAndStaysBelowWorkingWeight() {
        val sets = WarmUp.sets(Exercise.Bench, 150.0)
        assertTrue(sets.all { it.weightKg < 150.0 && it.weightKg % Strength.PLATE_STEP == 0.0 })
        assertEquals(WarmUpSet(20.0, 1, "15"), sets.first())
    }

    @Test
    fun noTableForAccessories() {
        assertTrue(WarmUp.sets(Exercise.BicepsCurl, 50.0).isEmpty())
    }
}
