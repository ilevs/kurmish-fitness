package com.kurmish.fitness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CoachTest {
    private val benchProfile = Profile(
        bodyweightKg = 82.0,
        discipline = Discipline.Bench,
        rank = Rank.Second,
        goal = Goal.Peak,
        daysPerWeek = 2,
    )

    @Test
    fun selectorFindsTwoDayPeakCycle() {
        assertEquals("СРЦ15", CycleCatalog.best(benchProfile)?.id)
    }

    @Test
    fun lightAthleteGetsIssue() {
        val match = CycleCatalog.select(benchProfile.copy(bodyweightKg = 55.0, daysPerWeek = 3)).first()
        assertTrue(match.issues.isNotEmpty())
    }

    @Test
    fun restAfterYesterdayAndHardFeelingLowersWeights() {
        val session = WorkoutSession(1, epochDay = 99, rawText = "", entries = emptyList(), feeling = Feeling.Hard)
        val advice = Coach.advise(AppData(benchProfile, listOf(session)), today = 100)
        assertTrue(advice.headline.contains("отдохнуть"))
        assertTrue(advice.points.any { it.contains("5–10 %") })
    }

    @Test
    fun easyFeelingNeedsNoAdvice() {
        val session = WorkoutSession(1, epochDay = 90, rawText = "", entries = emptyList(), feeling = Feeling.Easy)
        val advice = Coach.advise(AppData(benchProfile, listOf(session)), today = 100)
        assertTrue(advice.points.none { it.contains("Было легко") })
    }

    @Test
    fun journalEstimateIsUsedWhenNoManualMax() {
        val entry = ExerciseEntry(Exercise.Bench, sets = listOf(SetGroup(100.0, 5)))
        val data = AppData(benchProfile, listOf(WorkoutSession(1, 95, "", listOf(entry))))
        assertEquals(116.67, data.currentMax(Exercise.Bench, today = 100)!!, 0.01)
    }

    @Test
    fun storeRoundTrip() {
        val dir = Files.createTempDirectory("journal").toFile()
        val store = JournalStore(File(dir, "journal.json"))
        val data = AppData(
            benchProfile.copy(manualMaxes = mapOf(Exercise.Bench to 110.0)),
            listOf(WorkoutSession(1, 5, "жим 100 на 5", WorkoutParser.parse("жим 100 на 5").entries)),
        )
        store.save(data)
        assertEquals(data, store.load())
        dir.deleteRecursively()
    }
}
