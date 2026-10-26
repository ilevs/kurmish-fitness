package com.kurmish.fitness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyweightLoadTest {
    private fun session(id: Long, exercise: Exercise, weightKg: Double?, reps: Int) = WorkoutSession(
        id = id,
        epochDay = id,
        rawText = "",
        entries = listOf(ExerciseEntry(exercise, exercise.title, listOf(SetGroup(weightKg, reps, 1)))),
    )

    @Test
    fun gravitronCountsBodyweightMinusCounterweight() {
        val data = AppData(Profile(bodyweightKg = 90.0), listOf(session(1, Exercise.AssistedPullUp, 30.0, 8)))
        val max = data.workingMax(Exercise.AssistedPullUp, today = 2)
        assertEquals(76.0, max?.kg ?: 0.0, 0.01)
        assertEquals(max, data.workingMax(Exercise.PullUp, today = 2))
        assertTrue(Exercise.AssistedPullUp !in data.estimatedMaxes(today = 2))
    }

    @Test
    fun percentsBecomeCounterweightOnGravitron() {
        val item = ProgramItem("Подтягивания в гравитроне", "4×6", Exercise.AssistedPullUp, listOf(67))
        val planned = Planner.fromProgram(item, Exercise.AssistedPullUp, WorkingMax(114.0, bodyweightKg = 90.0))
        assertEquals(listOf(PlannedSets(4, "6", 12.5)), planned.sets)
        assertTrue(planned.notes.any { it.startsWith("Вес — противовес гравитрона") })
    }

    @Test
    fun repsGiveBeltWeightOrCounterweight() {
        val strong = WorkingMax(130.0, bodyweightKg = 90.0)
        assertEquals(15.0, WeightAdvice.byReps(Exercise.Dips, "5", strong, null)?.first ?: 0.0, 0.01)
        val weak = WorkingMax(80.0, bodyweightKg = 90.0)
        assertEquals(30.0, WeightAdvice.byReps(Exercise.AssistedDips, "8", weak, null)?.first ?: 0.0, 0.01)
        assertNull(WeightAdvice.byReps(Exercise.PullUp, "мах", strong, null))
    }

    @Test
    fun gravitronDipsAreRecognised() {
        val entry = WorkoutParser.parse("брусья в гравитроне 25 на 8").entries.single()
        assertEquals(Exercise.AssistedDips, entry.exercise)
    }
}
