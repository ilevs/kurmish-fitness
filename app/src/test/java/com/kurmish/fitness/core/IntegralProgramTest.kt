package com.kurmish.fitness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegralProgramTest {
    private val program = IntegralProgram.program

    @Test
    fun liftsRotateIndependently() {
        assertEquals(36, program.sessions.size)
        assertEquals(9, program.microcycles)
        val first = program.sessions.take(4).map { it.items.first().name }
        assertEquals(
            listOf(
                "Присед с паузой: синглы до максимума дня",
                "Жим лёжа: объём",
                "Становая тяга: динамика",
                "Жим лёжа: динамика",
            ),
            first,
        )
        val week4 = program.sessions.first { it.microcycle == 4 }.items.first()
        assertEquals("Присед: объём", week4.name)
        assertEquals(listOf(70), week4.percents)
    }

    @Test
    fun maxEffortVariationGetsWeightFromMainLift() {
        val maxes = mapOf(Exercise.Squat to 200.0)
        val profile = Profile(programId = "integral", programStartEpochDay = 0, manualMaxes = maxes)
        val item = Planner.today(AppData(profile), today = 0).items.first { it.part == PlanPart.Work }
        assertEquals(Exercise.PauseSquat, item.exercise)
        // ПМ присед с паузой ≈ 200 × 0,9 = 180 кг; 95 % — 171 → 170 кг.
        assertEquals(listOf(PlannedSets(1, "1", 170.0)), item.sets)
        assertTrue(Programs.byId("integral") != null)
    }

    @Test
    fun plateauSuggestsMoreFrequency() {
        fun bench(day: Long, kg: Double) = WorkoutSession(
            id = day,
            epochDay = day,
            rawText = "",
            entries = listOf(ExerciseEntry(Exercise.Bench, sets = listOf(SetGroup(kg, 3)))),
        )
        val sessions = listOf(bench(10, 100.0), bench(70, 95.0), bench(75, 97.5), bench(80, 100.0))
        val lines = Plateau.lines(AppData(sessions = sessions), today = 84)
        assertEquals(1, lines.size)
        assertTrue(lines.single().startsWith("«Жим лёжа» 4 недели без роста"))
        val growing = sessions + bench(82, 105.0)
        assertTrue(Plateau.lines(AppData(sessions = growing), today = 84).isEmpty())
    }
}
