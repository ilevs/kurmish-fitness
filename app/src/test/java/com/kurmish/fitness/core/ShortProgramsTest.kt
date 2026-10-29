package com.kurmish.fitness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortProgramsTest {
    @Test
    fun programsCoverOneToThreeDays() {
        assertEquals(
            "1 тренировка в неделю · 4 недели · цель: смешанный",
            ShortPrograms.maintenance.summary,
        )
        assertEquals("2 тренировки в неделю · 7 недель · цель: масса", ShortPrograms.fullBodyTwice.summary)
        assertEquals("3 тренировки в неделю · 8 недель · цель: масса", ShortPrograms.upperLowerThree.summary)
        assertEquals(14, ShortPrograms.fullBodyTwice.sessions.size)
        assertEquals(24, ShortPrograms.upperLowerThree.sessions.size)
        assertEquals(ShortPrograms.upperLowerThree, Programs.byId("upper-lower-three"))
        assertTrue(Programs.all.map { it.id }.let { it.size == it.toSet().size })
    }

    @Test
    fun upperAndLowerAlternateByWeek() {
        val days = ShortPrograms.upperLowerThree.sessions.take(6).map { it.day }
        assertEquals(listOf("Верх", "Низ", "Верх", "Низ", "Верх", "Низ"), days)
    }

    @Test
    fun weeksGoEasyThenHardThenDeload() {
        val bench = { week: Int ->
            ShortPrograms.upperLowerThree.sessions.first { it.microcycle == week && it.day == "Верх" }.items.first()
        }
        assertEquals("3×6-8, запас 2–3 повтора: неделя вполсилы", bench(1).scheme)
        assertEquals("4×6-8, запас 1–2 повтора", bench(3).scheme)
        assertEquals("2×6, разгрузка: вес прошлой недели, без отказа", bench(8).scheme)
        val deadlift = ShortPrograms.fullBodyTwice.sessions[5].items.first()
        assertEquals("Становая тяга", deadlift.name)
        assertEquals("3×5-6, запас 1–2 повтора", deadlift.scheme)
    }

    @Test
    fun everyExerciseIsRecognised() {
        val all = listOf(ShortPrograms.maintenance, ShortPrograms.fullBodyTwice, ShortPrograms.upperLowerThree)
        all.flatMap { it.sessions }.flatMap { it.items }.forEach { item ->
            assertEquals(item.name, Planner.exerciseIn(item.name)?.title)
        }
    }

    @Test
    fun maintenanceKeepsHeavyWeight() {
        val squat = ShortPrograms.maintenance.sessions.first().items.first()
        assertEquals("Присед 3×5 по 117,5 кг (78 %)", squat.describe(150.0))
        assertNull(ShortPrograms.maintenance.sessions.first().items[2].maxOf)
    }

    @Test
    fun planUsesDoubleProgression() {
        val bench = ExerciseEntry(Exercise.Bench, sets = listOf(SetGroup(80.0, 8, 3)))
        val data = AppData(
            Profile(programId = "full-body-twice", programStartEpochDay = 0, daysPerWeek = 2),
            listOf(WorkoutSession(1, epochDay = -4, rawText = "", entries = listOf(bench))),
        )
        val item = Planner.today(data, today = 0).items.first { it.exercise == Exercise.Bench }
        assertEquals(listOf(PlannedSets(3, "6-8", 82.5)), item.sets)
    }
}
