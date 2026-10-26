package com.kurmish.fitness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramsTest {
    @Test
    fun maximumHasFiveMicrocyclesOfFourDays() {
        assertEquals(20, Programs.maximum.sessions.size)
        assertEquals(5, Programs.maximum.microcycles)
        val last = Programs.maximum.sessions.last()
        assertEquals("Д4", last.day)
        assertEquals(listOf(90), last.items.first().percents)
        assertEquals("10×2", last.items.first().scheme)
    }

    @Test
    fun weightsFollowMax() {
        val first = Programs.maximum.sessions.first().items.first()
        assertEquals("Жим штанги лёжа 10×10 по 50 кг (50 %)", first.describe(100.0))
        val squat = Programs.hypertrophy.sessions.first().items.first()
        assertEquals(listOf(135.0, 140.0, 145.0, 155.0), squat.weights(200.0))
    }

    @Test
    fun progressCountsSessionsFromStart() {
        val sessions = (1..5).map {
            WorkoutSession(
                it.toLong(),
                epochDay = 100L + it,
                rawText = "",
                entries = emptyList()
            )
        }
        val progress = Programs.progress(Programs.maximum, startEpochDay = 103, sessions = sessions)
        assertEquals(3, progress.done)
        assertEquals("Д4", progress.next?.day)
        assertNull(Programs.progress(Programs.hypertrophy, 0, List(16) { sessions.first() }).next)
    }

    @Test
    fun coachShowsProgramPlanAndWarmUp() {
        val profile = Profile(
            programId = "maximum",
            programStartEpochDay = 10,
            manualMaxes = mapOf(Exercise.Squat to 150.0)
        )
        val bench = ExerciseEntry(Exercise.Bench, sets = listOf(SetGroup(50.0, 10, 10)))
        val advice = Coach.advise(AppData(profile, listOf(WorkoutSession(1, 10, "", listOf(bench)))), today = 12)
        assertTrue(advice.plan.first().contains("Д2, микроцикл 1 из 5"))
        assertTrue(advice.plan.any { it.contains("по 75 кг (50 %)") })
        assertEquals(WarmUpSet(20.0, 3, "5"), advice.warmUp.first())
    }
}
