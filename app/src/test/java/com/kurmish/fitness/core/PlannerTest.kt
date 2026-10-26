package com.kurmish.fitness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerTest {
    private val maxes = mapOf(Exercise.Squat to 100.0, Exercise.Bench to 80.0, Exercise.Deadlift to 120.0)

    @Test
    fun withoutProgramDayAHasConcreteWeights() {
        val plan = Planner.today(AppData(Profile(goal = Goal.Strength, manualMaxes = maxes)), today = 50)
        assertTrue(plan.title.startsWith("Общая тренировка А"))
        val work = plan.items.filter { it.part == PlanPart.Work }
        assertEquals(Exercise.Squat, work.first().exercise)
        assertEquals(listOf(PlannedSets(5, "5", 80.0)), work.first().sets)
        assertEquals("5 × 5 по 80 кг", work.first().sets.single().label)
        // Тяга в наклоне без своего ПМ считается от жима: 80 × 0,7 × 80 % ≈ 45 кг.
        assertEquals(45.0, work.first { it.exercise == Exercise.BentOverRow }.sets.single().weightKg)
    }

    @Test
    fun daysAlternateAndDeadliftRepsAreCapped() {
        val yesterday = WorkoutSession(1, epochDay = 49, rawText = "", entries = emptyList())
        val data = AppData(Profile(goal = Goal.Endurance, manualMaxes = maxes), listOf(yesterday))
        val plan = Planner.today(data, today = 50)
        assertTrue(plan.title.startsWith("Общая тренировка Б"))
        val deadlift = plan.items.first { it.exercise == Exercise.Deadlift }
        assertEquals(PlannedSets(3, "6", 72.5), deadlift.sets.single())
        assertEquals("3 × мах", plan.items.first { it.exercise == Exercise.PullUp }.sets.single().label)
    }

    @Test
    fun todaysRecordDoesNotShiftPlanAndClosesItem() {
        val squat = ExerciseEntry(Exercise.Squat, sets = listOf(SetGroup(80.0, 5, 5)))
        val data = AppData(Profile(manualMaxes = maxes), listOf(WorkoutSession(1, 50, "", listOf(squat))))
        val plan = Planner.today(data, today = 50)
        assertTrue(plan.title.startsWith("Общая тренировка А"))
        val item = plan.items.first { it.exercise == Exercise.Squat }
        assertEquals(listOf(squat), item.recordedIn(listOf(squat)))
    }

    @Test
    fun unknownMaxLeavesWeightToChoose() {
        val plan = Planner.today(AppData(Profile()), today = 50)
        val squat = plan.items.first { it.exercise == Exercise.Squat }
        assertNull(squat.sets.single().weightKg)
        assertTrue(plan.items.first().sets.isEmpty())
    }

    @Test
    fun programSchemeWithTailAndSeveralPercents() {
        val item = ProgramItem("Присед", "3×3, последний подход ×2", Exercise.Squat, listOf(87, 93, 100))
        val planned = Planner.fromProgram(item, 200.0)
        assertEquals(listOf(175.0, 185.0, 200.0), planned.sets.map { it.weightKg })
        assertEquals("последний подход ×2", planned.notes.first())
        val pullUps = ProgramItem("Подтягивания широким хватом", "3-4×мах", null, emptyList())
        val unknown = Planner.fromProgram(pullUps, null)
        assertEquals(Exercise.PullUp, unknown.exercise)
        assertEquals(listOf(PlannedSets(3, "мах", null)), unknown.sets)
    }

    @Test
    fun poolReplacesTemplateExerciseForTheCycle() {
        val profile = Profile(goal = Goal.Strength, manualMaxes = maxes).choose("Присед", Exercise.FrontSquat)
        val entry = ExerciseEntry(Exercise.FrontSquat, sets = listOf(SetGroup(60.0, 5)))
        val data = AppData(profile, listOf(WorkoutSession(1, 50, "", listOf(entry))))
        val work = Planner.today(data, today = 50).items.first { it.part == PlanPart.Work }
        assertEquals(Exercise.FrontSquat, work.exercise)
        assertEquals("Присед", work.slot)
        assertTrue(work.replaced)
        // ПМ фронтального по Дневнику: 60 × (1 + 5/30) = 70 кг, 80 % — 55 кг.
        assertEquals(55.0, work.sets.single().weightKg)
        assertEquals(emptyMap<String, Exercise>(), profile.choose("Присед", null).pool)
    }

    @Test
    fun poolReplacesProgramItemOnEveryDay() {
        val profile = Profile(programId = "hypertrophy", programStartEpochDay = 0)
            .choose("Приседания в гакк-тренажёре", Exercise.LegPress)
        val plan = Planner.today(AppData(profile), today = 5)
        val item = plan.items.first { it.slot == "Приседания в гакк-тренажёре" }
        assertEquals("Жим ногами", item.title)
        assertEquals(listOf(PlannedSets(4, "12", null)), item.sets)
    }

    @Test
    fun catalogHasMovementsAndUniqueSynonyms() {
        val synonyms = Exercise.entries.flatMap { it.synonyms }
        assertEquals(synonyms.size, synonyms.toSet().size)
        Movement.entries.forEach { movement -> assertTrue(Exercise.entries.any { it.movement == movement }) }
        assertEquals(Exercise.LegPress, WorkoutParser.parse("жим ногами 100 на 10").entries.single().exercise)
        val chinUps = WorkoutParser.parse("подтягивания обратным хватом 3 по 8")
        assertEquals(Exercise.ChinUp, chinUps.entries.single().exercise)
    }
}
