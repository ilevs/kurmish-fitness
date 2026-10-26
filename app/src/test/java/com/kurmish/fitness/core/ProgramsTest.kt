package com.kurmish.fitness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(listOf(120.0, 125.0, 132.5, 137.5), squat.weights(200.0))
        assertEquals("Присед с низкой штангой", Programs.renamed["Присед с низкой штангой в бинтах"])
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
    fun plannerShowsProgramDayWithWeightsAndWarmUp() {
        val profile = Profile(
            programId = "maximum",
            programStartEpochDay = 10,
            manualMaxes = mapOf(Exercise.Squat to 150.0)
        )
        val bench = ExerciseEntry(Exercise.Bench, sets = listOf(SetGroup(50.0, 10, 10)))
        val plan = Planner.today(AppData(profile, listOf(WorkoutSession(1, 10, "", listOf(bench)))), today = 12)
        assertEquals("Д2, микроцикл 1 из 5", plan.title)
        val squat = plan.items[1]
        assertEquals(Exercise.Squat, squat.exercise)
        assertEquals(listOf(PlannedSets(10, "10", 75.0)), squat.sets)
        assertEquals(PlanPart.WarmUp, plan.items.first().part)
        assertEquals(PlannedSets(3, "5", 20.0), plan.items.first().sets.first())
        assertEquals(PlanPart.CoolDown, plan.items.last().part)
    }

    @Test
    fun programsDescribeThemselvesAndCompareWithProfile() {
        assertEquals("4 тренировки в неделю · 5 недель · цель: сила", Programs.maximum.summary)
        assertEquals("4 тренировки в неделю · 4 недели · цель: масса", Programs.hypertrophy.summary)
        val profile = Profile(goal = Goal.Mass, daysPerWeek = 3)
        assertEquals(listOf("в профиле 3 тренировки в неделю"), Programs.hypertrophy.mismatches(profile))
        assertEquals(2, Programs.maximum.mismatches(profile).size)
        assertEquals("21 неделя", plural(21, "неделя", "недели", "недель"))
        assertEquals("12 недель", plural(12, "неделя", "недели", "недель"))
    }
}
