package com.kurmish.fitness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun testWorkoutComesFirstAndDisappearsOnceDiaryHasWeights() {
        val fresh = AppData(FitnessTest.applyTo(Profile(), ShoulderTests(Grade.Poor, null)))
        assertTrue(FitnessTest.needed(fresh, today = 10))
        val plan = Planner.today(fresh, today = 10)
        assertEquals("Тестовая тренировка", plan.title)
        assertEquals(PlanPart.WarmUp, plan.items.first().part)
        val work = plan.items.filter { it.part == PlanPart.Work }
        assertEquals(Exercise.MachineChestPress, work[1].exercise)
        assertTrue(work.all { item -> item.notes.any { it.startsWith("Подводящие") } })
        val squat = ExerciseEntry(Exercise.Squat, sets = listOf(SetGroup(80.0, 6)))
        val recorded = fresh.copy(sessions = listOf(WorkoutSession(1, 10, "", listOf(squat))))
        // В день теста план остаётся тестовым, пока его записываешь.
        assertTrue(FitnessTest.needed(recorded, today = 10))
        assertFalse(FitnessTest.needed(recorded, today = 11))
        assertFalse(FitnessTest.needed(AppData(Profile(manualMaxes = maxes)), today = 10))
    }

    @Test
    fun strengthBalanceComesFromDiary() {
        val entries = listOf(
            ExerciseEntry(Exercise.Bench, sets = listOf(SetGroup(100.0, 1))),
            ExerciseEntry(Exercise.ChinUp, sets = listOf(SetGroup(5.0, 1))),
            ExerciseEntry(Exercise.Squat, sets = listOf(SetGroup(100.0, 1))),
            ExerciseEntry(Exercise.Deadlift, sets = listOf(SetGroup(120.0, 1))),
        )
        val data = AppData(Profile(bodyweightKg = 80.0), listOf(WorkoutSession(1, 10, "", entries)))
        assertEquals(100.0, data.currentMax(Exercise.Bench, 10))
        val advice = FitnessTest.balance(data, today = 10)
        assertEquals(2, advice.size)
        assertTrue(advice[0].startsWith("Подтягивания обратным хватом"))
        assertTrue(advice[1].startsWith("Становая слабее"))
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
