package com.kurmish.fitness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessTestTest {
    private val maxes = mapOf(Exercise.Squat to 100.0, Exercise.Bench to 80.0, Exercise.Deadlift to 120.0)

    @Test
    fun weakShouldersGetSimplerPressAndPull() {
        val profile = FitnessTest.applyTo(Profile(manualMaxes = maxes), ShoulderTests(Grade.Poor, Grade.BelowAverage))
        assertEquals(Exercise.MachineChestPress, profile.pool["Жим лёжа"])
        assertEquals(Exercise.SeatedCableRow, profile.pool["Подтягивания"])
        val work = Planner.today(AppData(profile), today = 0).items.filter { it.part == PlanPart.Work }
        assertEquals(Exercise.MachineChestPress, work[1].exercise)
        assertTrue(work.first { it.exercise == Exercise.BentOverRow }.notes.contains(FitnessTest.STRETCH))
        val good = FitnessTest.applyTo(profile, ShoulderTests(Grade.Good, Grade.Excellent))
        assertTrue(good.pool.isEmpty())
    }

    @Test
    fun testSessionGivesMaxesAndStrengthBalance() {
        val results = mapOf(
            Exercise.Bench to (100.0 to 1),
            Exercise.ChinUp to (5.0 to 1),
            Exercise.Squat to (100.0 to 1),
            Exercise.Deadlift to (120.0 to 1),
            Exercise.PowerClean to (0.0 to 0),
        )
        val session = FitnessTest.session(results, today = 10)!!
        assertEquals(4, session.entries.size)
        val data = AppData(Profile(bodyweightKg = 80.0), listOf(session))
        assertEquals(100.0, data.currentMax(Exercise.Bench, 10))
        val advice = FitnessTest.balance(data, today = 10)
        assertEquals(2, advice.size)
        assertTrue(advice[0].startsWith("Подтягивания обратным хватом"))
        assertTrue(advice[1].startsWith("Становая слабее"))
        assertNull(FitnessTest.session(emptyMap(), today = 10))
    }

    @Test
    fun yearPlanWalksThroughPeriodsAndRepeats() {
        assertNull(YearPlan.position(null, 10))
        assertEquals(YearPosition(Period.Preparatory, 1, 0), YearPlan.position(0, 3))
        assertEquals(YearPosition(Period.Hypertrophy, 1, 1), YearPlan.position(0, 21))
        assertEquals(Period.Deload, YearPlan.position(0, 7L * 11)?.period)
        assertEquals(YearPosition(Period.Preparatory, 1, 0), YearPlan.position(0, 7L * YearPlan.totalWeeks))
        val plan = Planner.today(AppData(Profile(manualMaxes = maxes, yearPlanStartEpochDay = 0)), today = 7L * 12)
        assertTrue(plan.title.contains("период «Сила», неделя 1 из 8"))
        val squat = plan.items.first { it.exercise == Exercise.Squat }
        assertEquals(PlannedSets(5, "4", 82.5), squat.sets.single())
        assertTrue(squat.notes.last().startsWith("Темп 2010, отдых 3–5 мин"))
    }

    @Test
    fun orderChangesEveryTwoWeeks() {
        val sessions = (1L..6L).map { WorkoutSession(it, epochDay = it, rawText = "", entries = emptyList()) }
        val plan = Planner.today(AppData(Profile(manualMaxes = maxes, daysPerWeek = 3), sessions), today = 20)
        val work = plan.items.filter { it.part == PlanPart.Work }.map { it.exercise }
        val order = listOf(Exercise.Bench, Exercise.Squat, Exercise.Hyperextension, Exercise.BentOverRow)
        assertEquals(order, work.take(order.size))
    }

    @Test
    fun restDependsOnRepsAndPeriod() {
        assertEquals("Темп 2010, отдых 60 с", Pace.note(Exercise.FacePull, "15", null))
        assertEquals("Темп 2010, отдых 2–3 мин", Pace.note(Exercise.FrontSquat, "5", null))
        assertEquals("Темп 20X0, отдых 2–3 мин", Pace.note(Exercise.Squat, "3", Period.Power))
        assertEquals("Темп 3010, отдых 1,5–2 мин", Pace.note(Exercise.Bench, "10", Period.Hypertrophy))
    }
}
