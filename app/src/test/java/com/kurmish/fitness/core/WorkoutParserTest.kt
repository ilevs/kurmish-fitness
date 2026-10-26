package com.kurmish.fitness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutParserTest {
    private fun single(text: String): ExerciseEntry = WorkoutParser.parse(text).entries.single()

    @Test
    fun weightOnReps() {
        val entry = single("жим 100 на 5")
        assertEquals(Exercise.Bench, entry.exercise)
        assertEquals(listOf(SetGroup(100.0, 5, 1)), entry.sets)
    }

    @Test
    fun weightRepsSetsWithCyrillicX() {
        assertEquals(listOf(SetGroup(120.0, 5, 3)), single("Присед 120х5х3").sets)
    }

    @Test
    fun decimalCommaAndKg() {
        assertEquals(listOf(SetGroup(62.5, 8, 2)), single("жим лёжа 62,5 кг 8 раз 2 подхода").sets)
    }

    @Test
    fun setsByRepsWithWeightMark() {
        val entry = single("становая 3 подхода по 3 с весом 180")
        assertEquals(Exercise.Deadlift, entry.exercise)
        assertEquals(listOf(SetGroup(180.0, 3, 3)), entry.sets)
    }

    @Test
    fun bodyweightSetsByReps() {
        val entry = single("подтягивания 3 по 10")
        assertEquals(Exercise.PullUp, entry.exercise)
        assertEquals(listOf(SetGroup(null, 10, 3)), entry.sets)
    }

    @Test
    fun bodyweightTimesNotation() {
        assertEquals(listOf(SetGroup(null, 12, 4)), single("брусья 4x12").sets)
    }

    @Test
    fun setsAfterCommaBelongToPreviousLine() {
        assertEquals(listOf(SetGroup(100.0, 5, 3)), single("Жим сто на пять, три подхода").sets)
    }

    @Test
    fun numberWordsFromVoice() {
        val entry = single("жим сто двадцать два с половиной на три")
        assertEquals(listOf(SetGroup(122.5, 3, 1)), entry.sets)
    }

    @Test
    fun ladderOfSets() {
        val sets = single("жим 60x10, 80x5, 100x3x3").sets
        assertEquals(listOf(SetGroup(60.0, 10), SetGroup(80.0, 5), SetGroup(100.0, 3, 3)), sets)
    }

    @Test
    fun severalExercisesInOneSentence() {
        val parsed = WorkoutParser.parse("сделал присед 140 на 3 по 2 потом жим стоя 50 на 5 и тяга в наклоне 70x8x3")
        val expected = listOf(Exercise.Squat, Exercise.OverheadPress, Exercise.BentOverRow)
        assertEquals(expected, parsed.entries.map { it.exercise })
        assertEquals(SetGroup(140.0, 3, 2), parsed.entries[0].sets.single())
        assertEquals(SetGroup(70.0, 8, 3), parsed.entries[2].sets.single())
    }

    @Test
    fun unknownExerciseKeepsItsName() {
        val parsed = WorkoutParser.parse("жим 100 на 5. шраги 10 на 15")
        assertEquals(2, parsed.entries.size)
        assertEquals("Шраги", parsed.entries[1].displayName)
    }

    @Test
    fun feelingAndDate() {
        val parsed = WorkoutParser.parse("вчера жим 100 на 5, было тяжело")
        assertEquals(Feeling.Hard, parsed.feeling)
        assertEquals(-1, parsed.dayOffset)
        assertEquals(Feeling.Easy, WorkoutParser.parse("присед 100 на 5 легко").feeling)
        assertEquals(Feeling.Hard, WorkoutParser.parse("присед 100 на 5 не легко").feeling)
    }

    @Test
    fun garbageGivesNothing() {
        assertTrue(WorkoutParser.parse("просто поболтали").entries.isEmpty())
    }
}
