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
    fun withoutTableStepsAboutTwentyPercent() {
        val sets = WarmUp.sets(Exercise.DumbbellPress, 30.0, workingReps = 10)
        val expected = listOf(
            WarmUpSet(5.0, 1, "5"),
            WarmUpSet(12.5, 1, "5"),
            WarmUpSet(17.5, 1, "4"),
            WarmUpSet(25.0, 1, "4"),
            WarmUpSet(27.5, 1, "1-2"),
        )
        assertEquals(expected, sets)
        // Штанга без таблицы не легче пустого грифа, повторы не больше восьми.
        val curl = WarmUp.sets(Exercise.BicepsCurl, 50.0, workingReps = 20)
        assertEquals(WarmUpSet(20.0, 1, "8"), curl.first())
        assertTrue(curl.all { it.weightKg < 50.0 })
    }

    @Test
    fun noSetsForBodyweightAndAssisted() {
        assertTrue(WarmUp.sets(Exercise.PullUp, 10.0).isEmpty())
        assertTrue(WarmUp.sets(Exercise.AssistedPullUp, 40.0).isEmpty())
    }

    @Test
    fun routineDependsOnFirstMovement() {
        val squat = WarmUp.routine(Exercise.Squat)
        assertTrue(squat.first().contains("не бега"))
        assertTrue(squat.any { "голеностоп" in it })
        val bench = WarmUp.routine(Exercise.Bench)
        assertTrue(bench.none { "велотренажёра" in it })
        assertTrue(bench.any { "тяга блока к груди" in it })
        assertEquals(3, WarmUp.routine(null).size)
    }
}
