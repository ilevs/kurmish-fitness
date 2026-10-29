package com.kurmish.fitness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleProgressionTest {
    @Test
    fun weightGrowsWhenEverySetHitsTop() {
        val next = DoubleProgression.next(Exercise.Squat, "6-8", 3, listOf(SetGroup(100.0, 8, 3)))
        assertEquals(102.5, next?.first)
        assertTrue(next?.second.orEmpty().contains("+2,5 кг"))
    }

    @Test
    fun weightHoldsUntilTopIsReached() {
        val sets = listOf(SetGroup(100.0, 8, 2), SetGroup(100.0, 7))
        assertEquals(100.0, DoubleProgression.next(Exercise.Squat, "6-8", 3, sets)?.first)
        val fewer = listOf(SetGroup(100.0, 8, 2))
        assertEquals(100.0, DoubleProgression.next(Exercise.Squat, "6-8", 3, fewer)?.first)
        val warmUp = listOf(SetGroup(60.0, 5), SetGroup(100.0, 8, 3))
        assertEquals(102.5, DoubleProgression.next(Exercise.Squat, "6-8", 3, warmUp)?.first)
    }

    @Test
    fun dumbbellsStepByOneKilogram() {
        val next = DoubleProgression.next(Exercise.LateralRaise, "12-15", 2, listOf(SetGroup(10.0, 15, 2)))
        assertEquals(11.0, next?.first)
        assertTrue(next?.second.orEmpty().endsWith("Вес одной гантели"))
    }

    @Test
    fun noRangeOrNoHistoryMeansNoAdvice() {
        assertNull(DoubleProgression.next(Exercise.Squat, "5", 3, listOf(SetGroup(100.0, 5, 3))))
        assertNull(DoubleProgression.next(Exercise.Squat, "6-8", 3, emptyList()))
        assertNull(DoubleProgression.next(Exercise.PullUp, "6-8", 3, listOf(SetGroup(10.0, 8, 3))))
    }

    @Test
    fun todayDoesNotShiftThePlan() {
        val data = AppData(
            Profile(),
            listOf(
                WorkoutSession(1, 5, "", listOf(ExerciseEntry(Exercise.Bench, sets = listOf(SetGroup(80.0, 8))))),
                WorkoutSession(2, 9, "", listOf(ExerciseEntry(Exercise.Bench, sets = listOf(SetGroup(85.0, 6))))),
            ),
        )
        assertEquals(listOf(SetGroup(80.0, 8)), data.previousSets(Exercise.Bench, 9))
        assertEquals(listOf(SetGroup(85.0, 6)), data.previousSets(Exercise.Bench, 10))
    }
}
