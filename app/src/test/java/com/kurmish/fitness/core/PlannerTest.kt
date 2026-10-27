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
    fun everyGeneralWorkoutHasEachMovementType() {
        val types = setOf(Movement.Rotation, Movement.Core, Movement.Squat, Movement.Hinge)
        val dayA = Planner.today(AppData(Profile(manualMaxes = maxes)), today = 50)
        val yesterday = WorkoutSession(1, epochDay = 49, rawText = "", entries = emptyList())
        val dayB = Planner.today(AppData(Profile(manualMaxes = maxes), listOf(yesterday)), today = 50)
        listOf(dayA, dayB).forEach { plan ->
            val work = plan.items.filter { it.part == PlanPart.Work }.mapNotNull { it.exercise }
            assertEquals(6, work.size)
            assertTrue(work.map { it.movement }.containsAll(types))
            assertTrue(work.any { it.movement.isPush } && work.any { it.movement.isPull })
        }
        val split = dayB.items.first { it.exercise == Exercise.SplitSquat }
        assertEquals("3 × 10", split.sets.single().label.substringBefore(" по"))
        assertTrue(split.notes.first().contains("каждую ногу"))
    }

    @Test
    fun warmUpWithoutTableRampsToFirstWeight() {
        val profile = Profile(
            goal = Goal.Strength,
            manualMaxes = maxes + (Exercise.GobletSquat to 50.0),
            pool = mapOf("Присед" to Exercise.GobletSquat),
        )
        val warmUp = Planner.today(AppData(profile), today = 50).items.first()
        assertEquals(PlanPart.WarmUp, warmUp.part)
        assertTrue(warmUp.notes.first().contains("не бега"))
        // Рабочий 40 кг на 5: шаги около 20 %, последний на 90 % на 1–2 повтора.
        assertEquals(PlannedSets(1, "1-2", 35.0), warmUp.sets.last())
        assertEquals(7.5, warmUp.sets.first().weightKg)
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
        val planned = Planner.fromProgram(item, Exercise.Squat, WorkingMax(200.0))
        assertEquals(listOf(175.0, 185.0, 200.0), planned.sets.map { it.weightKg })
        assertEquals("последний подход ×2", planned.notes.first())
        val pullUps = ProgramItem("Подтягивания широким хватом", "3-4×мах", null, emptyList())
        val unknown = Planner.fromProgram(pullUps, Exercise.PullUp, null)
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

    @Test
    fun missingMaxIsEstimatedFromRelatedLift() {
        val profile = Profile(programId = "hypertrophy", programStartEpochDay = 0, manualMaxes = maxes)
            .choose("Присед с низкой штангой", Exercise.StiffLegDeadlift)
        val item = Planner.today(AppData(profile), today = 0).items.first { it.part == PlanPart.Work }
        assertEquals(Exercise.StiffLegDeadlift, item.exercise)
        // ПМ тяги на прямых ногах ≈ 120 × 0,7 = 84 кг; 60 % — 50,4 → 50 кг под блины.
        assertEquals(50.0, item.sets.first().weightKg)
        assertTrue(item.notes.any { "«Становая тяга»" in it })
        assertEquals(WorkingMax(84.0, Exercise.Deadlift), AppData(profile).workingMax(Exercise.StiffLegDeadlift, 0))
        assertNull(AppData(profile).workingMax(Exercise.ExternalRotation, 0))
    }

    @Test
    fun accessoryWeightComesFromDiary() {
        val extensions = ExerciseEntry(Exercise.LegExtension, sets = listOf(SetGroup(35.0, 12), SetGroup(40.0, 12, 2)))
        val data = AppData(
            Profile(programId = "hypertrophy", programStartEpochDay = 0),
            listOf(WorkoutSession(1, epochDay = -3, rawText = "", entries = listOf(extensions))),
        )
        val plan = Planner.today(data, today = 0)
        val item = plan.items.first { it.exercise == Exercise.LegExtension }
        assertEquals(listOf(PlannedSets(3, "12", 40.0)), item.sets)
        assertTrue(item.notes.last().startsWith("Вес из последней записи"))
        assertEquals(Exercise.HackSquat, plan.items.first { it.title.contains("гакк") }.exercise)
        assertEquals(Exercise.Abs, plan.items.first { it.title.contains("закреплён") }.exercise)
    }

    @Test
    fun ownMaxGivesWeightForReps() {
        val item = ProgramItem("Фронтальный присед", "3×10", null, emptyList())
        val planned = Planner.fromProgram(item, Exercise.FrontSquat, WorkingMax(100.0))
        // 100 / (1 + 12/30) ≈ 71,4 → 72,5 кг: 10 повторов с запасом 2.
        assertEquals(listOf(PlannedSets(3, "10", 72.5)), planned.sets)
    }

    @Test
    fun withoutAnyMaxWeightsStartFromBodyweight() {
        val profile = Profile(programId = "hypertrophy", programStartEpochDay = 0, bodyweightKg = 90.0)
        val data = AppData(profile)
        // Присед новичка ≈ 0,8 × 90 = 72 кг.
        assertEquals(WorkingMax(72.0, fromBodyweight = true), data.workingMax(Exercise.Squat, 0))
        val plan = Planner.today(data, today = 0)
        val squat = plan.items.first { it.exercise == Exercise.Squat }
        assertTrue(squat.sets.all { it.weightKg != null })
        assertTrue(squat.notes.any { "собственному весу" in it })
        // Разгибания ≈ 0,35 × 72 = 25,2; на 12 повторов с запасом 2: 25,2 / (1 + 14/30) ≈ 17,2 → 17,5 кг.
        val extensions = plan.items.first { it.exercise == Exercise.LegExtension }
        assertEquals(17.5, extensions.sets.single().weightKg)
        assertEquals(Exercise.SeatedCalfRaise, plan.items.first { it.title == "Икры сидя" }.exercise)
        val calves = plan.items.first { it.title == "Икры стоя в тренажёре" }
        assertEquals(Exercise.StandingCalfRaise, calves.exercise)
        assertTrue(calves.sets.single().weightKg != null)
    }

    @Test
    fun replacementWithMaxRepsGetsWeightAndDumbbellNote() {
        val profile = Profile(programId = "hypertrophy", programStartEpochDay = 0, manualMaxes = maxes)
            .choose("Приседания с закреплёнными ногами", Exercise.DumbbellPress)
        val item = Planner.today(AppData(profile), today = 0).items.first { it.exercise == Exercise.DumbbellPress }
        // Жим гантелей ≈ 0,3 × 80 = 24 кг; на 10 повторов с запасом 2: 24 / 1,4 ≈ 17,1 → 17,5 кг.
        assertEquals(PlannedSets(4, "мах", 17.5), item.sets.single())
        assertTrue(item.notes.last().endsWith("Вес одной гантели"))
        assertEquals(Exercise.Pullover, WorkoutParser.parse("пуловер 20 на 12").entries.single().exercise)
    }

    @Test
    fun planBecomesEditableDiaryDraft() {
        val data = AppData(Profile(goal = Goal.Strength, manualMaxes = maxes))
        val plan = Planner.today(data, today = 50)
        val draft = SessionDraft.fromPlan(plan, epochDay = 50, existing = null)
        assertEquals(0L, draft.id)
        assertEquals(Exercise.Squat, draft.entries.first().exercise)
        assertEquals(SetDraft(weight = "80", reps = "5", sets = "5"), draft.entries.first().sets.single())
        assertTrue(draft.valid)
        val squat = ExerciseEntry(Exercise.Squat, sets = listOf(SetGroup(80.0, 5, 5)))
        val existing = WorkoutSession(7, 50, "присед 80 на 5 по 5", listOf(squat))
        val continued = SessionDraft.fromPlan(plan, epochDay = 50, existing = existing)
        assertEquals(7L, continued.id)
        assertEquals(1, continued.entries.count { it.exercise == Exercise.Squat })
        assertEquals(Exercise.Bench, continued.entries[1].exercise)
    }
}
