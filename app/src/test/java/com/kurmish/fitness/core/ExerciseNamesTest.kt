package com.kurmish.fitness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseNamesTest {
    @Test
    fun resolveFindsExerciseByTitleAndSynonym() {
        assertEquals(Exercise.Bench, ExerciseNames.resolve("  жим лежа "))
        assertEquals(Exercise.Bench, ExerciseNames.resolve("Жим лёжа"))
        assertEquals(Exercise.PullUp, ExerciseNames.resolve("подтягивания"))
        assertNull(ExerciseNames.resolve("шраги"))
    }

    @Test
    fun bookExercisesResolveAndOldOnesKeepTheirPlace() {
        assertEquals(Exercise.TBarRow, ExerciseNames.resolve("Тяга Т-грифа"))
        assertEquals(Exercise.ZercherSquat, ExerciseNames.resolve("зерхер"))
        assertEquals(Exercise.ReverseHyperextension, ExerciseNames.resolve("обратные гиперы"))
        assertEquals(Exercise.OneArmDumbbellSnatch, ExerciseNames.resolve("рывок гантели"))
        assertTrue(Exercise.PauseDeadlift.ordinal < Exercise.TPushUp.ordinal)
        val parsed = WorkoutParser.parse("тяга штанги в наклоне обратным хватом 60 на 8, жим в смите сидя 40 на 10")
        assertEquals(listOf(Exercise.UnderhandRow, Exercise.SmithShoulderPress), parsed.entries.map { it.exercise })
        val old = WorkoutParser.parse("тяга штанги в наклоне 60 на 8, жим гантелей 20 на 10, мостик 3x15")
        assertEquals(
            listOf(Exercise.BentOverRow, Exercise.DumbbellPress, Exercise.GluteBridge),
            old.entries.map { it.exercise },
        )
    }

    @Test
    fun everyMovementHasLadderOfSteps() {
        val ladders = Movement.entries.filter { it != Movement.Arms }
        ladders.forEach { movement ->
            val levels = Exercise.entries.filter { it.movement == movement }.map { it.level }.toSet()
            assertTrue("$movement", levels.size >= 4)
        }
    }

    @Test
    fun suggestStartsWithTypedTextAndMatchesWords() {
        assertTrue(ExerciseNames.suggest("жим", emptyList()).contains("Жим лёжа"))
        assertTrue(ExerciseNames.suggest("лежа", emptyList()).contains("Жим лёжа"))
        assertTrue(ExerciseNames.suggest("при", emptyList()).contains("Присед"))
    }

    @Test
    fun suggestPutsOwnNamesFirstAndHidesExactMatch() {
        val own = listOf("Шраги")
        assertEquals("Шраги", ExerciseNames.suggest("ш", own).first())
        assertTrue(ExerciseNames.suggest("Шраги", own).none { it == "Шраги" })
        assertEquals(ExerciseNames.LIMIT, ExerciseNames.suggest("", own).size.coerceAtMost(ExerciseNames.LIMIT))
    }

    @Test
    fun customNamesComeFromDiaryNewestFirstWithoutRepeats() {
        fun session(id: Long, day: Long, name: String) = WorkoutSession(
            id = id,
            epochDay = day,
            rawText = "",
            entries = listOf(
                ExerciseEntry(null, name, listOf(SetGroup(null, 10))),
                ExerciseEntry(Exercise.Bench, sets = listOf(SetGroup(100.0, 5))),
            ),
        )
        val sessions = listOf(session(1, 10, "Шраги"), session(2, 20, "Прогулка фермера"), session(3, 5, "шраги"))
        val data = AppData(sessions = sessions)
        assertEquals(listOf("Прогулка фермера", "Шраги"), data.customExerciseNames())
    }
}
