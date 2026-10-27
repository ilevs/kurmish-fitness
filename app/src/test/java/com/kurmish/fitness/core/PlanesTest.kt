package com.kurmish.fitness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanesTest {
    @Test
    fun heaviestLiftStandsOnFifthStep() {
        listOf(Exercise.Squat, Exercise.Deadlift, Exercise.Bench, Exercise.BentOverRow).forEach {
            assertEquals("$it", 5, it.level)
        }
        assertTrue(Exercise.FrontSquat.level < Exercise.Squat.level)
        assertTrue(Exercise.PauseDeadlift.level < Exercise.Deadlift.level)
    }

    @Test
    fun sixthStepIsOneSided() {
        assertEquals(6, Exercise.SingleLegDeadlift.level)
        assertEquals(6, Exercise.SingleDumbbellPress.level)
        assertTrue(Exercise.OneArmCablePress.level < Exercise.Bench.level)
    }

    @Test
    fun externalRotationIsHeaviestOnFourth() {
        assertEquals(4, Exercise.PowerClean.level)
        assertTrue(Exercise.Snatch.level > Exercise.PowerClean.level)
    }

    @Test
    fun nonPlanarExercisesStayOutOfPlanes() {
        listOf(Exercise.StandingCalfRaise, Exercise.SeatedCalfRaise, Exercise.LateralRaise).forEach {
            assertEquals("$it", Movement.Arms, it.movement)
        }
    }

    @Test
    fun isometricCoreIsSimplerThanCrunches() {
        assertTrue(Exercise.Plank.level < Exercise.Abs.level)
        assertTrue(Exercise.SidePlank.level < Exercise.SideCrunch.level)
        assertEquals(CoreZone.Side, Planes.coreZone(Exercise.SidePlank))
        assertEquals(CoreZone.Front, Planes.coreZone(Exercise.Plank))
        assertNull(Planes.coreZone(Exercise.Squat))
    }

    @Test
    fun ladderCoversEveryExerciseInOrder() {
        Movement.entries.forEach { movement ->
            val ladder = Planes.ladder(movement)
            assertEquals(ladder.map { it.level }.sorted(), ladder.map { it.level })
            assertEquals(
                Exercise.entries.count { it.movement == movement },
                ladder.sumOf { it.exercises.size },
            )
        }
        assertTrue(Planes.discouraged.keys.all { it.level <= 2 })
    }
}
