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
